-- Trilha de auditoria: cada fato do sistema, uma vez (PK messageId), nunca alterado.
CREATE TABLE audit_events (
    seq             BIGSERIAL    NOT NULL,
    message_id      UUID         PRIMARY KEY,
    topic           VARCHAR(100) NOT NULL,
    kafka_partition INT          NOT NULL,
    kafka_offset    BIGINT       NOT NULL,
    event_type      VARCHAR(60),
    producer        VARCHAR(60),
    correlation_id  VARCHAR(100),
    causation_id    UUID,
    order_id        UUID,
    seller_id       VARCHAR(64),
    skus            TEXT[],
    occurred_at     TIMESTAMPTZ  NOT NULL,
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    payload         JSONB        NOT NULL
);
CREATE INDEX audit_order_idx ON audit_events (order_id, occurred_at) WHERE order_id IS NOT NULL;
CREATE INDEX audit_correlation_idx ON audit_events (correlation_id, occurred_at);
CREATE INDEX audit_seller_idx ON audit_events (seller_id, occurred_at) WHERE seller_id IS NOT NULL;
CREATE INDEX audit_skus_idx ON audit_events USING GIN (skus);

CREATE FUNCTION audit_is_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_events e imutavel: % proibido', TG_OP;
END;
$$;

CREATE TRIGGER audit_events_no_change
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_is_immutable();
