-- Um pagamento por pedido (UNIQUE order_id = idempotencia de negocio).
-- Invariante de dinheiro no banco: 0 <= estornado <= capturado <= autorizado.
CREATE TABLE payments (
    id                 UUID          PRIMARY KEY,
    order_id           UUID          NOT NULL UNIQUE,
    buyer_id           VARCHAR(64)   NOT NULL,
    amount             NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    status             VARCHAR(20)   NOT NULL CHECK (status IN (
                           'AUTHORIZING', 'AUTHORIZED', 'DECLINED', 'CAPTURED', 'VOIDED', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    captured_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    refunded_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    decline_reason     VARCHAR(60),
    gateway_ref        VARCHAR(80),
    reconcile_attempts INT           NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT money_invariant CHECK (refunded_amount >= 0 AND refunded_amount <= captured_amount AND captured_amount <= amount)
);
CREATE INDEX payments_unknown_idx ON payments (created_at) WHERE status = 'AUTHORIZING';

-- LIVRO-RAZAO: so INSERT. Cada movimento de dinheiro uma vez (UNIQUE por tipo e referencia).
CREATE TABLE payment_ledger (
    id          BIGSERIAL     PRIMARY KEY,
    payment_id  UUID          NOT NULL REFERENCES payments (id),
    entry_type  VARCHAR(16)   NOT NULL CHECK (entry_type IN ('AUTHORIZATION', 'CAPTURE', 'REFUND', 'VOID')),
    amount      NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    reference   VARCHAR(64)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (payment_id, entry_type, reference)
);

CREATE FUNCTION ledger_is_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'payment_ledger e append-only: % proibido', TG_OP;
END;
$$;

CREATE TRIGGER payment_ledger_no_change
    BEFORE UPDATE OR DELETE ON payment_ledger
    FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
