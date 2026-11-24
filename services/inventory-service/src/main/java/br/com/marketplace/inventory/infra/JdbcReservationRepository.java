package br.com.marketplace.inventory.infra;

import br.com.marketplace.inventory.domain.ReservationRepository;
import br.com.marketplace.inventory.domain.ReservationStatus;
import br.com.marketplace.inventory.domain.ReservedLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReservationRepository implements ReservationRepository {

    private final JdbcClient jdbc;

    JdbcReservationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReservationStatus> status(UUID orderId) {
        return jdbc.sql("SELECT status FROM reservations WHERE order_id = :id")
                .param("id", orderId)
                .query((rs, n) -> ReservationStatus.valueOf(rs.getString("status")))
                .optional();
    }

    @Override
    public void create(UUID orderId, ReservationStatus status, List<ReservedLine> lines) {
        jdbc.sql("INSERT INTO reservations (order_id, status) VALUES (:id, :status)")
                .param("id", orderId)
                .param("status", status.name())
                .update();
        for (ReservedLine line : lines) {
            jdbc.sql("INSERT INTO reservation_lines (order_id, sku, quantity) VALUES (:id, :sku, :q)")
                    .param("id", orderId)
                    .param("sku", line.sku())
                    .param("q", line.quantity())
                    .update();
        }
    }

    @Override
    public List<ReservedLine> lines(UUID orderId) {
        return jdbc.sql("SELECT sku, quantity FROM reservation_lines WHERE order_id = :id ORDER BY sku")
                .param("id", orderId)
                .query((rs, n) -> new ReservedLine(rs.getString("sku"), rs.getInt("quantity")))
                .list();
    }

    @Override
    public void setStatus(UUID orderId, ReservationStatus status) {
        jdbc.sql("UPDATE reservations SET status = :status, updated_at = now() WHERE order_id = :id")
                .param("status", status.name())
                .param("id", orderId)
                .update();
    }

    @Override
    public boolean registerRestock(UUID orderId, String sellerId) {
        return jdbc.sql("INSERT INTO restocks (order_id, seller_id) VALUES (:id, :seller) ON CONFLICT DO NOTHING")
                        .param("id", orderId)
                        .param("seller", sellerId)
                        .update()
                == 1;
    }
}
