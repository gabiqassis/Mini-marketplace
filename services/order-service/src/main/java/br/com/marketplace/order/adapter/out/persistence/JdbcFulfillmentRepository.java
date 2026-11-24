package br.com.marketplace.order.adapter.out.persistence;

import br.com.marketplace.order.domain.Fulfillment;
import br.com.marketplace.order.application.port.out.FulfillmentRepository;
import br.com.marketplace.order.domain.FulfillmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFulfillmentRepository implements FulfillmentRepository {

    private static final RowMapper<Fulfillment> MAPPER = (rs, n) -> new Fulfillment(
            rs.getObject("order_id", UUID.class),
            rs.getString("seller_id"),
            rs.getBigDecimal("amount"),
            FulfillmentStatus.valueOf(rs.getString("status")),
            rs.getString("problem"),
            rs.getString("invoice_key"),
            rs.getString("tracking_code"));

    private final JdbcClient jdbc;

    JdbcFulfillmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertAll(List<Fulfillment> fulfillments) {
        for (Fulfillment f : fulfillments) {
            jdbc.sql("""
                            INSERT INTO fulfillments (order_id, seller_id, amount, status)
                            VALUES (:order, :seller, :amount, :status)
                            ON CONFLICT DO NOTHING
                            """)
                    .param("order", f.orderId())
                    .param("seller", f.sellerId())
                    .param("amount", f.amount())
                    .param("status", f.status().name())
                    .update();
        }
    }

    @Override
    public List<Fulfillment> findByOrder(UUID orderId) {
        return jdbc.sql("SELECT * FROM fulfillments WHERE order_id = :id ORDER BY seller_id").param("id", orderId).query(MAPPER).list();
    }

    @Override
    public Optional<Fulfillment> find(UUID orderId, String sellerId) {
        return jdbc.sql("SELECT * FROM fulfillments WHERE order_id = :id AND seller_id = :seller")
                .param("id", orderId)
                .param("seller", sellerId)
                .query(MAPPER)
                .optional();
    }

    @Override
    public void update(Fulfillment f) {
        jdbc.sql("""
                        UPDATE fulfillments
                           SET status = :status, problem = :problem, invoice_key = :key, tracking_code = :tracking, updated_at = now()
                         WHERE order_id = :order AND seller_id = :seller
                        """)
                .param("status", f.status().name())
                .param("problem", f.problem())
                .param("key", f.invoiceKey())
                .param("tracking", f.trackingCode())
                .param("order", f.orderId())
                .param("seller", f.sellerId())
                .update();
    }
}
