package br.com.marketplace.payment.infra;

import br.com.marketplace.payment.domain.Payment;
import br.com.marketplace.payment.domain.PaymentRepository;
import br.com.marketplace.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPaymentRepository implements PaymentRepository {

    private static final RowMapper<Payment> MAPPER = (rs, n) -> new Payment(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getString("buyer_id"),
            rs.getBigDecimal("amount"),
            PaymentStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("captured_amount"),
            rs.getBigDecimal("refunded_amount"),
            rs.getString("decline_reason"),
            rs.getString("gateway_ref"));

    private final JdbcClient jdbc;

    JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(Payment p) {
        return jdbc.sql("""
                        INSERT INTO payments (id, order_id, buyer_id, amount, status)
                        VALUES (:id, :order, :buyer, :amount, :status)
                        ON CONFLICT (order_id) DO NOTHING
                        """)
                        .param("id", p.id())
                        .param("order", p.orderId())
                        .param("buyer", p.buyerId())
                        .param("amount", p.amount())
                        .param("status", p.status().name())
                        .update()
                == 1;
    }

    @Override
    public Optional<Payment> findByOrder(UUID orderId) {
        return jdbc.sql("SELECT * FROM payments WHERE order_id = :id").param("id", orderId).query(MAPPER).optional();
    }

    @Override
    public Optional<Payment> lockByOrder(UUID orderId) {
        return jdbc.sql("SELECT * FROM payments WHERE order_id = :id FOR UPDATE").param("id", orderId).query(MAPPER).optional();
    }

    @Override
    public void update(Payment p) {
        jdbc.sql("""
                        UPDATE payments
                           SET status = :status, captured_amount = :captured, refunded_amount = :refunded,
                               decline_reason = :reason, gateway_ref = :ref, updated_at = now()
                         WHERE id = :id
                        """)
                .param("status", p.status().name())
                .param("captured", p.capturedAmount())
                .param("refunded", p.refundedAmount())
                .param("reason", p.declineReason())
                .param("ref", p.gatewayRef())
                .param("id", p.id())
                .update();
    }

    @Override
    public boolean appendLedger(UUID paymentId, String type, BigDecimal amount, String reference) {
        return jdbc.sql("""
                        INSERT INTO payment_ledger (payment_id, entry_type, amount, reference)
                        VALUES (:id, :type, :amount, :ref)
                        ON CONFLICT (payment_id, entry_type, reference) DO NOTHING
                        """)
                        .param("id", paymentId)
                        .param("type", type)
                        .param("amount", amount)
                        .param("ref", reference)
                        .update()
                == 1;
    }

    @Override
    public List<LedgerEntry> ledger(UUID paymentId) {
        return jdbc.sql("SELECT entry_type, amount, reference, created_at FROM payment_ledger WHERE payment_id = :id ORDER BY id")
                .param("id", paymentId)
                .query((rs, n) -> new LedgerEntry(rs.getString("entry_type"), rs.getBigDecimal("amount"),
                        rs.getString("reference"), rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public List<Payment> findAuthorizingOlderThan(Instant olderThan, int limit) {
        return jdbc.sql("SELECT * FROM payments WHERE status = 'AUTHORIZING' AND created_at < :t ORDER BY created_at LIMIT :n")
                .param("t", OffsetDateTime.ofInstant(olderThan, ZoneOffset.UTC))
                .param("n", limit)
                .query(MAPPER)
                .list();
    }

    @Override
    public int incrementAttempts(UUID paymentId) {
        return jdbc.sql("UPDATE payments SET reconcile_attempts = reconcile_attempts + 1 WHERE id = :id RETURNING reconcile_attempts")
                .param("id", paymentId)
                .query(Integer.class)
                .single();
    }
}
