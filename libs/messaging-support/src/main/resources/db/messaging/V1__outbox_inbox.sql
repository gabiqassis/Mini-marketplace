-- Tabelas de mensageria presentes em TODO servico (cada um no seu proprio schema).
-- As migrations de negocio de cada servico comecam em V2.

-- OUTBOX: a mensagem e gravada na MESMA transacao do dado de negocio.
-- O relay publica depois (Kafka para fatos, RabbitMQ para comandos). Sem dual-write.
CREATE TABLE outbox (
    seq          BIGSERIAL    PRIMARY KEY,
    id           UUID         NOT NULL UNIQUE,   -- messageId do envelope
    destination  VARCHAR(8)   NOT NULL DEFAULT 'KAFKA' CHECK (destination IN ('KAFKA', 'RABBIT')),
    topic        TEXT         NOT NULL,          -- topico Kafka ou routing key RabbitMQ
    msg_key      TEXT         NOT NULL,
    body         JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX outbox_pending_idx ON outbox (seq) WHERE published_at IS NULL;

-- INBOX: mensagem repetida nao faz efeito duas vezes.
-- Gravada na MESMA transacao do efeito; a PK recusa o segundo insert.
CREATE TABLE inbox (
    message_id   UUID         NOT NULL,
    consumer     TEXT         NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, consumer)
);
