CREATE TABLE items (
    sku        VARCHAR(64)   PRIMARY KEY,
    seller_id  VARCHAR(64)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    price      NUMERIC(12,2) NOT NULL CHECK (price > 0),
    sequence   BIGINT        NOT NULL CHECK (sequence >= 0),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX items_seller_idx ON items (seller_id);
