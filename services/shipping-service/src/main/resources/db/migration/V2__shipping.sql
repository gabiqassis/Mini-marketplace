-- Um envio por (pedido, vendedor): evento repetido nao cria segundo envio nem segunda etiqueta.
CREATE TABLE shipments (
    id                UUID         PRIMARY KEY,
    order_id          UUID         NOT NULL,
    seller_id         VARCHAR(64)  NOT NULL,
    status            VARCHAR(20)  NOT NULL CHECK (status IN (
                          'AWAITING_INVOICE', 'CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY',
                          'DELIVERY_FAILED', 'DELIVERED', 'LOST', 'RETURNED')),
    tracking_code     VARCHAR(20)  UNIQUE,
    carrier           VARCHAR(40),
    destination       VARCHAR(200) NOT NULL,
    delivery_attempts INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (order_id, seller_id),
    CONSTRAINT label_after_invoice CHECK (status = 'AWAITING_INVOICE' OR tracking_code IS NOT NULL)
);

-- Linha do tempo (onde esta a encomenda). Guarda tambem eventos IGNORADOS (fora de ordem) para auditoria.
CREATE TABLE tracking_events (
    id          BIGSERIAL    PRIMARY KEY,
    shipment_id UUID         NOT NULL REFERENCES shipments (id),
    status      VARCHAR(20)  NOT NULL,
    location    VARCHAR(200),
    description VARCHAR(300),
    occurred_at TIMESTAMPTZ  NOT NULL,
    accepted    BOOLEAN      NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX tracking_events_shipment_idx ON tracking_events (shipment_id, occurred_at);

-- Deduplicacao de webhook: a transportadora reenvia ate receber 2xx.
CREATE TABLE carrier_events (
    event_id    VARCHAR(100) PRIMARY KEY,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
