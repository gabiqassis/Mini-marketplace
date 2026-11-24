package br.com.marketplace.shipping.application.service;

import br.com.marketplace.shipping.application.port.in.TrackingQueryUseCase;
import br.com.marketplace.shipping.application.port.in.TrackingQueryUseCase.ShipmentView;
import br.com.marketplace.shipping.application.port.in.TrackingQueryUseCase.TrackingView;
import br.com.marketplace.shipping.domain.Shipment;
import br.com.marketplace.shipping.application.port.out.ShipmentRepository;
import br.com.marketplace.shipping.application.port.out.TrackingCache;
import br.com.marketplace.shipping.domain.TrackingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** "Onde esta minha encomenda?": cache-aside no Redis, banco como verdade. */
@Service
public class TrackingQueryService implements TrackingQueryUseCase {

    private final ShipmentRepository shipments;
    private final TrackingCache cache;
    private final ObjectMapper mapper;

    public TrackingQueryService(ShipmentRepository shipments, TrackingCache cache, ObjectMapper mapper) {
        this.shipments = shipments;
        this.cache = cache;
        this.mapper = mapper;
    }

    @Override
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
