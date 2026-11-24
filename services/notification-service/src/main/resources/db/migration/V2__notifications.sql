CREATE TABLE seller_contacts (
    seller_id VARCHAR(64)  PRIMARY KEY,
    email     VARCHAR(200) NOT NULL
);

CREATE TABLE order_contacts (
    order_id UUID         PRIMARY KEY,
    buyer_id VARCHAR(64)  NOT NULL,
    email    VARCHAR(200) NOT NULL
);

-- dedupe_key = fato de origem + destinatario + modelo: o mesmo fato nunca gera o mesmo aviso duas vezes.
CREATE TABLE notifications (
    id             UUID         PRIMARY KEY,
    order_id       UUID,
    recipient_type VARCHAR(8)   NOT NULL CHECK (recipient_type IN ('SELLER', 'BUYER')),
    recipient_id   VARCHAR(64)  NOT NULL,
    email          VARCHAR(200) NOT NULL,
    template       VARCHAR(40)  NOT NULL,
    subject        VARCHAR(200) NOT NULL,
    body           TEXT         NOT NULL,
    status         VARCHAR(8)   NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    failure_reason VARCHAR(300),
    dedupe_key     VARCHAR(200) NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ
);
CREATE INDEX notifications_order_idx ON notifications (order_id);
