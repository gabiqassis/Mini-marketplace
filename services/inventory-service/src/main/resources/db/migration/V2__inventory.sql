-- TRAVAS NO BANCO: mesmo que o codigo erre, estoque nunca fica negativo nem reservado alem do disponivel.
CREATE TABLE stock (
    sku        VARCHAR(64) PRIMARY KEY,
    on_hand    INT         NOT NULL CHECK (on_hand >= 0),
    reserved   INT         NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    sequence   BIGINT      NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reserved_le_on_hand CHECK (reserved <= on_hand)
);

-- Uma reserva por pedido (PK = idempotencia de negocio).
-- RELEASED sem linhas = "lapide": a liberacao chegou ANTES da reserva; a reserva tardia sera recusada.
CREATE TABLE reservations (
    order_id   UUID        PRIMARY KEY,
    status     VARCHAR(16) NOT NULL CHECK (status IN ('RESERVED', 'REJECTED', 'COMMITTED', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation_lines (
    order_id UUID        NOT NULL REFERENCES reservations (order_id),
    sku      VARCHAR(64) NOT NULL,
    quantity INT         NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, sku)
);

-- Reposicao por devolucao: uma vez por (pedido, vendedor). A PK e a idempotencia de negocio.
CREATE TABLE restocks (
    order_id    UUID        NOT NULL,
    seller_id   VARCHAR(64) NOT NULL,
    restocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (order_id, seller_id)
);
