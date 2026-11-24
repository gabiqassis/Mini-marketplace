CREATE TABLE seller_profiles (
    seller_id  VARCHAR(64)  PRIMARY KEY,
    legal_name VARCHAR(200) NOT NULL,
    cnpj       VARCHAR(20)  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Numeracao sequencial por vendedor e serie (exigencia fiscal: sem buracos por retry).
CREATE TABLE invoice_counters (
    seller_id   VARCHAR(64) NOT NULL,
    series      INT         NOT NULL,
    last_number BIGINT      NOT NULL CHECK (last_number >= 0),
    PRIMARY KEY (seller_id, series)
);

-- Uma nota por (pedido, vendedor): comando repetido nao gera segunda nota.
CREATE TABLE invoices (
    id               UUID          PRIMARY KEY,
    order_id         UUID          NOT NULL,
    seller_id        VARCHAR(64)   NOT NULL,
    series           INT           NOT NULL,
    number           BIGINT        NOT NULL,
    amount           NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    status           VARCHAR(12)   NOT NULL CHECK (status IN ('ISSUING', 'AUTHORIZED', 'REJECTED', 'FAILED')),
    access_key       CHAR(44),
    protocol         VARCHAR(20),
    rejection_code   VARCHAR(20),
    rejection_reason VARCHAR(300),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (order_id, seller_id),
    UNIQUE (seller_id, series, number),
    CONSTRAINT authorized_has_key CHECK (status <> 'AUTHORIZED' OR access_key IS NOT NULL)
);
