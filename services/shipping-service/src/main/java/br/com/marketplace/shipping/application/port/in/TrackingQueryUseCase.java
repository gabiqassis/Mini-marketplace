package br.com.marketplace.shipping.application.port.in;

import br.com.marketplace.shipping.domain.Shipment;
import br.com.marketplace.shipping.domain.TrackingEvent;
import java.util.List;
import java.util.UUID;

/** Porta de entrada: consulta de rastreio de um pedido. */
public interface TrackingQueryUseCase {

    record ShipmentView(Shipment shipment, List<TrackingEvent> timeline) {}

    record TrackingView(UUID orderId, List<ShipmentView> shipments, boolean fromCache) {}

    TrackingView byOrder(UUID orderId);
}
