package br.com.marketplace.order.adapter.out.persistence;

import br.com.marketplace.order.domain.IdempotentOrder;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.domain.OrderItem;
import br.com.marketplace.order.application.port.out.OrderRepository;
import br.com.marketplace.order.domain.OrderStatus;
import br.com.marketplace.order.domain.ShippingAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrderRepository implements OrderRepository {

    private static final String COLUMNS = """
            id, buyer_id, buyer_email, zip_code, street, street_number, city, state,
            status, total, cancel_reason, capture_requested
            """;

    private final JdbcClient jdbc;

    JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(Order order, String idempotencyKey, String requestHash) {
        ShippingAddress a = order.address();
        int inserted = jdbc.sql("""
                        INSERT INTO orders (id, buyer_id, buyer_email, zip_code, street, street_number, city, state,
                                            status, total, idempotency_key, request_hash)
                        VALUES (:id, :buyer, :email, :zip, :street, :number, :city, :state, :status, :total, :key, :hash)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        """)
                .param("id", order.id())
                .param("buyer", order.buyerId())
                .param("email", order.buyerEmail())
                .param("zip", a.zipCode())
                .param("street", a.street())
                .param("number", a.number())
                .param("city", a.city())
                .param("state", a.state())
                .param("status", order.status().name())
                .param("total", order.total())
                .param("key", idempotencyKey)
                .param("hash", requestHash)
                .update();
        if (inserted == 0) {
            return false;
        }
        for (OrderItem item : order.items()) {
            jdbc.sql("""
                            INSERT INTO order_items (order_id, sku, seller_id, title, quantity, unit_price)
                            VALUES (:id, :sku, :seller, :title, :q, :price)
                            """)
                    .param("id", order.id())
                    .param("sku", item.sku())
                    .param("seller", item.sellerId())
                    .param("title", item.title())
                    .param("q", item.quantity())
                    .param("price", item.unitPrice())
                    .update();
        }
        return true;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE id = :id")
                .param("id", id)
                .query(this::toOrder)
                .optional();
    }

    @Override
    public Optional<Order> lockById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(this::toOrder)
                .optional();
    }

    @Override
    public Optional<IdempotentOrder> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("SELECT " + COLUMNS + ", request_hash FROM orders WHERE idempotency_key = :key")
                .param("key", idempotencyKey)
                .query((rs, n) -> new IdempotentOrder(toOrder(rs, n), rs.getString("request_hash")))
                .optional();
    }

    @Override
    public boolean transition(UUID id, OrderStatus from, OrderStatus to, String reason) {
        return jdbc.sql("""
                        UPDATE orders
                           SET status = :to,
                               cancel_reason = COALESCE(CAST(:reason AS VARCHAR), cancel_reason),
                               version = version + 1,
                               updated_at = now()
                         WHERE id = :id AND status = :from
                        """)
                        .param("to", to.name())
                        .param("reason", reason)
                        .param("id", id)
                        .param("from", from.name())
                        .update()
                == 1;
    }

    @Override
    public void markCaptureRequested(UUID id) {
        jdbc.sql("UPDATE orders SET capture_requested = true, updated_at = now() WHERE id = :id").param("id", id).update();
    }

    @Override
    public List<UUID> findStaleIds(OrderStatus status, Instant olderThan, int limit) {
        return jdbc.sql("SELECT id FROM orders WHERE status = :status AND updated_at < :t ORDER BY updated_at LIMIT :n")
                .param("status", status.name())
                .param("t", OffsetDateTime.ofInstant(olderThan, ZoneOffset.UTC))
                .param("n", limit)
                .query((rs, n) -> rs.getObject("id", UUID.class))
                .list();
    }

    private Order toOrder(ResultSet rs, int n) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<OrderItem> items = jdbc.sql("""
                        SELECT sku, seller_id, title, quantity, unit_price FROM order_items WHERE order_id = :id ORDER BY sku
                        """)
                .param("id", id)
                .query((r, i) -> new OrderItem(r.getString("sku"), r.getString("seller_id"), r.getString("title"),
                        r.getInt("quantity"), r.getBigDecimal("unit_price")))
                .list();
        ShippingAddress address = new ShippingAddress(rs.getString("zip_code"), rs.getString("street"),
                rs.getString("street_number"), rs.getString("city"), rs.getString("state"));
        return new Order(id, rs.getString("buyer_id"), rs.getString("buyer_email"), address,
                OrderStatus.valueOf(rs.getString("status")), rs.getBigDecimal("total"), rs.getString("cancel_reason"),
                rs.getBoolean("capture_requested"), items);
    }
}
