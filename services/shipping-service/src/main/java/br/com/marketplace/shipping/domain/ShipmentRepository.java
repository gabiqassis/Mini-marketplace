package br.com.marketplace.shipping.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {

    /** ON CONFLICT (order_id, seller_id) DO NOTHING. */
    void insertIfAbsent(Shipment shipment);

    Optional<Shipment> lock(UUID orderId, String sellerId);

    Optional<Shipment> lockByTrackingCode(String trackingCode);

    void update(Shipment shipment);

    List<Shipment> findByOrder(UUID orderId);

    void addTrackingEvent(UUID shipmentId, TrackingEvent event);

    List<TrackingEvent> trackingEvents(UUID shipmentId);

    /** Deduplicacao de webhook pelo id do evento da transportadora. @return false se ja recebido. */
    boolean registerCarrierEvent(String eventId);
}
