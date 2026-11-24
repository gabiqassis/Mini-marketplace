CREATE TABLE orders (
    id                UUID          PRIMARY KEY,
    buyer_id          VARCHAR(64)   NOT NULL,
    buyer_email       VARCHAR(200)  NOT NULL,
    zip_code          CHAR(8)       NOT NULL,
    street            VARCHAR(200)  NOT NULL,
    street_number     VARCHAR(20)   NOT NULL,
    city              VARCHAR(100)  NOT NULL,
    state             CHAR(2)       NOT NULL,
    status            VARCHAR(20)   NOT NULL CHECK (status IN (
                          'PENDING', 'AWAITING_PAYMENT', 'CONFIRMED', 'INVOICED', 'IN_DELIVERY', 'DELIVERED',
                          'PARTIALLY_DELIVERED', 'REFUNDING', 'REFUNDED', 'ON_HOLD', 'CANCELLING', 'CANCELLED')),
    total             NUMERIC(14,2) NOT NULL CHECK (total > 0),
    cancel_reason     VARCHAR(200),
    capture_requested BOOLEAN       NOT NULL DEFAULT false,
    -- Idempotency-Key da API: UNIQUE garante que o mesmo POST repetido nao cria outro pedido
    idempotency_key   VARCHAR(100)  NOT NULL UNIQUE,
    request_hash      CHAR(64)      NOT NULL,  -- mesma chave com corpo diferente = erro 422
    version           INT           NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX orders_status_updated_idx ON orders (status, updated_at);

CREATE TABLE order_items (
    order_id   UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sku        VARCHAR(64)   NOT NULL,
    seller_id  VARCHAR(64)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    quantity   INT           NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    PRIMARY KEY (order_id, sku)
);

-- Uma parte por vendedor: nota fiscal e envio dele. PK impede parte duplicada.
CREATE TABLE fulfillments (
    order_id      UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    seller_id     VARCHAR(64)   NOT NULL,
    amount        NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    status        VARCHAR(20)   NOT NULL CHECK (status IN (
                      'AWAITING_INVOICE', 'INVOICE_FAILED', 'INVOICED', 'IN_TRANSIT', 'DELIVERED', 'REFUND_PENDING', 'REFUNDED')),
    problem       VARCHAR(60),
    invoice_key   CHAR(44),
    tracking_code VARCHAR(40),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (order_id, seller_id)
);

-- Copia local de preco/vendedor (alimentada por catalog.item-upserted): pedido nao chama o catalogo.
CREATE TABLE product_snapshot (
    sku        VARCHAR(64)   PRIMARY KEY,
    seller_id  VARCHAR(64)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    price      NUMERIC(12,2) NOT NULL CHECK (price > 0),
    sequence   BIGINT        NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
