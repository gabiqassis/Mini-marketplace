-- Um lote por (vendedor, batchId): a PK e a idempotencia do reenvio.
CREATE TABLE batches (
    seller_id   VARCHAR(64) NOT NULL,
    batch_id    VARCHAR(64) NOT NULL,
    source      VARCHAR(8)  NOT NULL CHECK (source IN ('SQS', 'API')),
    accepted    INT         NOT NULL CHECK (accepted >= 0),
    rejected    INT         NOT NULL CHECK (rejected >= 0),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (seller_id, batch_id)
);

CREATE TABLE batch_items (
    seller_id VARCHAR(64)  NOT NULL,
    batch_id  VARCHAR(64)  NOT NULL,
    position  INT          NOT NULL,
    sku       VARCHAR(64),
    status    VARCHAR(8)   NOT NULL CHECK (status IN ('ACCEPTED', 'REJECTED')),
    reason    VARCHAR(200),
    PRIMARY KEY (seller_id, batch_id, position),
    FOREIGN KEY (seller_id, batch_id) REFERENCES batches (seller_id, batch_id)
);
