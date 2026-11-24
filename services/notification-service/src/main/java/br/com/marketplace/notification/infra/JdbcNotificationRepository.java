package br.com.marketplace.notification.infra;

import br.com.marketplace.notification.domain.Draft;
import br.com.marketplace.notification.domain.Notification;
import br.com.marketplace.notification.domain.NotificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNotificationRepository implements NotificationRepository {

    private static final RowMapper<Notification> MAPPER = (rs, n) -> new Notification(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            Draft.Recipient.valueOf(rs.getString("recipient_type")),
            rs.getString("recipient_id"),
            rs.getString("email"),
            rs.getString("template"),
            rs.getString("subject"),
            rs.getString("body"),
            Notification.Status.valueOf(rs.getString("status")));

    private final JdbcClient jdbc;

    JdbcNotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(Notification n, String dedupeKey) {
        return jdbc.sql("""
                        INSERT INTO notifications (id, order_id, recipient_type, recipient_id, email, template, subject, body, status, dedupe_key)
                        VALUES (:id, :order, :type, :rid, :email, :template, :subject, :body, 'PENDING', :key)
                        ON CONFLICT (dedupe_key) DO NOTHING
                        """)
                        .param("id", n.id()).param("order", n.orderId()).param("type", n.recipient().name())
                        .param("rid", n.recipientId()).param("email", n.email()).param("template", n.template())
                        .param("subject", n.subject()).param("body", n.body()).param("key", dedupeKey)
                        .update()
                == 1;
    }

    @Override
    public Optional<Notification> find(UUID id) {
        return jdbc.sql("SELECT * FROM notifications WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    @Override
    public void markSent(UUID id) {
        jdbc.sql("UPDATE notifications SET status = 'SENT', sent_at = now() WHERE id = :id AND status = 'PENDING'").param("id", id).update();
    }

    @Override
    public void markFailed(UUID id, String reason) {
        jdbc.sql("UPDATE notifications SET status = 'FAILED', failure_reason = :r WHERE id = :id AND status = 'PENDING'")
                .param("id", id).param("r", reason == null ? null : reason.substring(0, Math.min(300, reason.length()))).update();
    }

    @Override
    public List<Notification> findByOrder(UUID orderId) {
        return jdbc.sql("SELECT * FROM notifications WHERE order_id = :o ORDER BY created_at").param("o", orderId).query(MAPPER).list();
    }

    @Override
    public void saveSellerEmail(String sellerId, String email) {
        jdbc.sql("""
                        INSERT INTO seller_contacts (seller_id, email) VALUES (:s, :e)
                        ON CONFLICT (seller_id) DO UPDATE SET email = EXCLUDED.email
                        """)
                .param("s", sellerId).param("e", email).update();
    }

    @Override
    public Optional<String> sellerEmail(String sellerId) {
        return jdbc.sql("SELECT email FROM seller_contacts WHERE seller_id = :s").param("s", sellerId).query(String.class).optional();
    }

    @Override
    public void saveBuyerEmail(UUID orderId, String buyerId, String email) {
        jdbc.sql("INSERT INTO order_contacts (order_id, buyer_id, email) VALUES (:o, :b, :e) ON CONFLICT DO NOTHING")
                .param("o", orderId).param("b", buyerId).param("e", email).update();
    }

    @Override
    public Optional<String> buyerEmail(UUID orderId) {
        return jdbc.sql("SELECT email FROM order_contacts WHERE order_id = :o").param("o", orderId).query(String.class).optional();
    }

    @Override
    public Optional<String> buyerId(UUID orderId) {
        return jdbc.sql("SELECT buyer_id FROM order_contacts WHERE order_id = :o").param("o", orderId).query(String.class).optional();
    }
}
