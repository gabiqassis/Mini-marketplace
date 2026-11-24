package br.com.marketplace.shipping.adapter.in.web;

import br.com.marketplace.shipping.application.port.in.TrackingQueryUseCase;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TrackingController {

    private final TrackingQueryUseCase tracking;

    TrackingController(TrackingQueryUseCase tracking) {
        this.tracking = tracking;
    }

    @GetMapping("/shipments/{orderId}")
    ResponseEntity<TrackingQueryUseCase.TrackingView> byOrder(@PathVariable UUID orderId) {
        TrackingQueryUseCase.TrackingView view = tracking.byOrder(orderId);
        return view.shipments().isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }
}
