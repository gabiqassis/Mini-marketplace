package br.com.marketplace.audit.infra;

import br.com.marketplace.audit.domain.AuditEvent;
import br.com.marketplace.audit.domain.AuditRepository;
import java.sql.Array;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditRepository implements AuditRepository {

    private static final RowMapper<AuditEvent> MAPPER = (rs, n) -> {
        Array skus = rs.getArray("skus");
        return new AuditEvent(
                rs.getObject("message_id", UUID.class),
                rs.getString("topic"),
                rs.getInt("kafka_partition"),
                rs.getLong("kafka_offset"),
                rs.getString("event_type"),
                rs.getString("producer"),
                rs.getString("correlation_id"),
                rs.getObject("causation_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("seller_id"),
                skus == null ? List.of() : Arrays.asList((String[]) skus.getArray()),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("payload"));
    };

    private final JdbcClient jdbc;

    JdbcAuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean append(AuditEvent e) {
        return jdbc.sql("""
                        INSERT INTO audit_events (message_id, topic, kafka_partition, kafka_offset, event_type, producer,
                                                  correlation_id, causation_id, order_id, seller_id, skus, occurred_at, payload)
                        VALUES (:id, :topic, :p, :o, :type, :producer, :corr, :cause, :order, :seller,
                                string_to_array(:skus, ','), :at, CAST(:payload AS jsonb))
                        ON CONFLICT (message_id) DO NOTHING
                        """)
                        .param("id", e.messageId()).param("topic", e.topic()).param("p", e.partition()).param("o", e.offset())
                        .param("type", e.type()).param("producer", e.producer()).param("corr", e.correlationId())
                        .param("cause", e.causationId()).param("order", e.orderId()).param("seller", e.sellerId())
                        .param("skus", e.skus().isEmpty() ? null : String.join(",", e.skus()))
                        .param("at", Timestamp.from(e.occurredAt())).param("payload", e.payloadJson())
                        .update()
                == 1;
    }

    @Override
    public List<AuditEvent> byOrder(UUID orderId) {
        return jdbc.sql("SELECT * FROM audit_events WHERE order_id = :o ORDER BY occurred_at, seq")
                .param("o", orderId).query(MAPPER).list();
    }

    @Override
    public List<AuditEvent> bySkus(Collection<String> skus) {
        return jdbc.sql("SELECT * FROM audit_events WHERE skus && string_to_array(:skus, ',') ORDER BY occurred_at, seq")
                .param("skus", String.join(",", skus)).query(MAPPER).list();
    }

    @Override
    public List<AuditEvent> byCorrelation(String correlationId) {
        return jdbc.sql("SELECT * FROM audit_events WHERE correlation_id = :c ORDER BY occurred_at, seq")
                .param("c", correlationId).query(MAPPER).list();
    }
}
