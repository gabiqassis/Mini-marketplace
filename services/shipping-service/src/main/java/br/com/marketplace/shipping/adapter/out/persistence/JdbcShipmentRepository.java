package br.com.marketplace.shipping.adapter.out.persistence;

import br.com.marketplace.shipping.domain.Shipment;
import br.com.marketplace.shipping.application.port.out.ShipmentRepository;
import br.com.marketplace.shipping.domain.ShipmentStatus;
import br.com.marketplace.shipping.domain.TrackingEvent;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcShipmentRepository implements ShipmentRepository {

    private static final RowMapper<Shipment> MAPPER = (rs, n) -> new Shipment(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getString("seller_id"),
            ShipmentStatus.valueOf(rs.getString("status")),
            rs.getString("tracking_code"),
            rs.getString("carrier"),
            rs.getString("destination"),
            rs.getInt("delivery_attempts"));

    private final JdbcClient jdbc;

    JdbcShipmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertIfAbsent(Shipment s) {
        jdbc.sql("""
                        INSERT INTO shipments (id, order_id, seller_id, status, destination)
                        VALUES (:id, :o, :s, :status, :dest)
                        ON CONFLICT (order_id, seller_id) DO NOTHING
                        """)
                .param("id", s.id()).param("o", s.orderId()).param("s", s.sellerId())
                .param("status", s.status().name()).param("dest", s.destination())
                .update();
    }

    @Override
    public Optional<Shipment> lock(UUID orderId, String sellerId) {
        return jdbc.sql("SELECT * FROM shipments WHERE order_id = :o AND seller_id = :s FOR UPDATE")
                .param("o", orderId).param("s", sellerId).query(MAPPER).optional();
    }

    @Override
    public Optional<Shipment> lockByTrackingCode(String code) {
        return jdbc.sql("SELECT * FROM shipments WHERE tracking_code = :c FOR UPDATE").param("c", code).query(MAPPER).optional();
    }

    @Override
    public void update(Shipment s) {
        jdbc.sql("""
                        UPDATE shipments SET status = :status, tracking_code = :code, carrier = :carrier,
                               delivery_attempts = :attempts, updated_at = now()
                         WHERE id = :id
                        """)
                .param("status", s.status().name()).param("code", s.trackingCode()).param("carrier", s.carrier())
                .param("attempts", s.deliveryAttempts()).param("id", s.id())
                .update();
    }

    @Override
    public List<Shipment> findByOrder(UUID orderId) {
        return jdbc.sql("SELECT * FROM shipments WHERE order_id = :o ORDER BY seller_id").param("o", orderId).query(MAPPER).list();
    }

    @Override
    public void addTrackingEvent(UUID shipmentId, TrackingEvent e) {
        jdbc.sql("""
                        INSERT INTO tracking_events (shipment_id, status, location, description, occurred_at, accepted)
                        VALUES (:id, :status, :loc, :desc, :at, :accepted)
                        """)
                .param("id", shipmentId).param("status", e.status()).param("loc", e.location())
                .param("desc", e.description()).param("at", Timestamp.from(e.occurredAt())).param("accepted", e.accepted())
                .update();
    }

    /**
     * Ordem de CHEGADA (id), nao o horario informado pela transportadora: relogios de sistemas
     * diferentes nao batem, e a ordem de chegada e a que a maquina de estados validou.
     */
    @Override
    public List<TrackingEvent> trackingEvents(UUID shipmentId) {
        return jdbc.sql("""
                        SELECT status, location, description, occurred_at, accepted
                          FROM tracking_events WHERE shipment_id = :id ORDER BY id
                        """)
                .param("id", shipmentId)
                .query((rs, n) -> new TrackingEvent(rs.getString("status"), rs.getString("location"), rs.getString("description"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getBoolean("accepted")))
                .list();
    }

    @Override
    public boolean registerCarrierEvent(String eventId) {
        return jdbc.sql("INSERT INTO carrier_events (event_id) VALUES (:id) ON CONFLICT DO NOTHING").param("id", eventId).update() == 1;
    }
}
