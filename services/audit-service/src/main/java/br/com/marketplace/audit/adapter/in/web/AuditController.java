package br.com.marketplace.audit.adapter.in.web;

import br.com.marketplace.audit.application.port.in.AuditUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Visao interna/auditoria. Em producao: rede interna + perfil de acesso de auditor. */
@RestController
@RequestMapping("/audit")
class AuditController {

    private final AuditUseCase audit;

    AuditController(AuditUseCase audit) {
        this.audit = audit;
    }

    @GetMapping("/orders/{orderId}")
    ResponseEntity<AuditUseCase.OrderTrail> order(@PathVariable UUID orderId) {
        AuditUseCase.OrderTrail trail = audit.orderTrail(orderId);
        return trail.timeline().isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(trail);
    }

    @GetMapping("/items/{sku}")
    List<AuditUseCase.Step> item(@PathVariable String sku) {
        return audit.itemTrail(sku);
    }

    @GetMapping("/correlations/{correlationId}")
    List<AuditUseCase.Step> correlation(@PathVariable String correlationId) {
        return audit.correlationTrail(correlationId);
    }
}
