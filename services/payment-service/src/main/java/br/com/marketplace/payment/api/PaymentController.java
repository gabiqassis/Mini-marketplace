package br.com.marketplace.payment.api;

import br.com.marketplace.payment.application.PaymentQueryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
class PaymentController {

    private final PaymentQueryService query;

    PaymentController(PaymentQueryService query) {
        this.query = query;
    }

    @GetMapping("/by-order/{orderId}")
    ResponseEntity<PaymentQueryService.PaymentView> byOrder(@PathVariable UUID orderId) {
        return query.byOrder(orderId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
