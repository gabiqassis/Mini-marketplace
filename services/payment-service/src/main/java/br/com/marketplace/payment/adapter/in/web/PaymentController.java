package br.com.marketplace.payment.adapter.in.web;

import br.com.marketplace.payment.application.port.in.PaymentQueryUseCase;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
class PaymentController {

    private final PaymentQueryUseCase query;

    PaymentController(PaymentQueryUseCase query) {
        this.query = query;
    }

    @GetMapping("/by-order/{orderId}")
    ResponseEntity<PaymentQueryUseCase.PaymentView> byOrder(@PathVariable UUID orderId) {
        return query.byOrder(orderId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
