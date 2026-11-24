package br.com.marketplace.shipping.application;

import br.com.marketplace.shipping.domain.Shipment;
import br.com.marketplace.shipping.domain.ShipmentRepository;
import br.com.marketplace.shipping.domain.TrackingCache;
import br.com.marketplace.shipping.domain.TrackingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** "Onde esta minha encomenda?": cache-aside no Redis, banco como verdade. */
@Service
public class TrackingQueryService {

    public record ShipmentView(Shipment shipment, List<TrackingEvent> timeline) {}

    public record TrackingView(UUID orderId, List<ShipmentView> shipments, boolean fromCache) {}

    private final ShipmentRepository shipments;
    private final TrackingCache cache;
    private final ObjectMapper mapper;

    public TrackingQueryService(ShipmentRepository shipments, TrackingCache cache, ObjectMapper mapper) {
        this.shipments = shipments;
        this.cache = cache;
        this.mapper = mapper;
    }

    public TrackingView byOrder(UUID orderId) {
        Optional<String> cached = cache.get(orderId);
        if (cached.isPresent()) {
            try {
                TrackingView v = mapper.readValue(cached.get(), TrackingView.class);
                return new TrackingView(v.orderId(), v.shipments(), true);
            } catch (JsonProcessingException e) {
                cache.evict(orderId);
            }
        }
        List<ShipmentView> views = shipments.findByOrder(orderId).stream()
                .map(s -> new ShipmentView(s, shipments.trackingEvents(s.id())))
                .toList();
        TrackingView view = new TrackingView(orderId, views, false);
        if (!views.isEmpty()) {
            try {
                cache.put(orderId, mapper.writeValueAsString(view));
            } catch (JsonProcessingException ignored) {
                // cache e opcional
            }
        }
        return view;
    }
}
