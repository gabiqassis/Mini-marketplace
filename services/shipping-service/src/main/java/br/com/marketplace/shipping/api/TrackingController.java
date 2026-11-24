package br.com.marketplace.shipping.api;

import br.com.marketplace.shipping.application.TrackingQueryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TrackingController {

    private final TrackingQueryService tracking;

    TrackingController(TrackingQueryService tracking) {
        this.tracking = tracking;
    }

    @GetMapping("/shipments/{orderId}")
    ResponseEntity<TrackingQueryService.TrackingView> byOrder(@PathVariable UUID orderId) {
        TrackingQueryService.TrackingView view = tracking.byOrder(orderId);
        return view.shipments().isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }
}
