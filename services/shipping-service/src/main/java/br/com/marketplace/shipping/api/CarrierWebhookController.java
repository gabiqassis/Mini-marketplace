package br.com.marketplace.shipping.api;

import br.com.marketplace.shipping.application.ShipmentService;
import br.com.marketplace.shipping.application.WebhookSignature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook da transportadora. Recebe o corpo BRUTO (String) para verificar a assinatura antes de
 * qualquer parse. Respostas pensadas para o retry do remetente:
 * 401 assinatura invalida | 200 duplicado (pare de reenviar) | 202 aceito | 404 rastreio desconhecido.
 */
@RestController
class CarrierWebhookController {

    private static final Logger log = LoggerFactory.getLogger(CarrierWebhookController.class);

    private final ShipmentService shipments;
    private final WebhookSignature signature;
    private final ObjectMapper mapper;
    private final Clock clock;

    CarrierWebhookController(ShipmentService shipments, WebhookSignature signature, ObjectMapper mapper, Clock clock) {
        this.shipments = shipments;
        this.signature = signature;
        this.mapper = mapper;
        this.clock = clock;
    }

    @PostMapping("/carrier/webhooks")
    ResponseEntity<Map<String, String>> receive(
            @RequestHeader(value = "X-Carrier-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Carrier-Signature", required = false) String sig,
            @RequestBody String rawBody) {
        if (!signature.isValid(timestamp, rawBody, sig, Instant.now(clock))) {
            log.warn("Webhook com assinatura invalida ou expirada recusado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "INVALID_SIGNATURE"));
        }
        ShipmentService.CarrierEvent event;
        try {
            event = mapper.readValue(rawBody, ShipmentService.CarrierEvent.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(Map.of("result", "INVALID_BODY"));
        }
        if (event.eventId() == null || event.trackingCode() == null || event.status() == null) {
            return ResponseEntity.badRequest().body(Map.of("result", "MISSING_FIELDS"));
        }
        ShipmentService.WebhookOutcome outcome = shipments.onCarrierEvent(event);
        HttpStatus status = switch (outcome) {
            case DUPLICATE -> HttpStatus.OK;
            case UNKNOWN_TRACKING -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(Map.of("result", outcome.name()));
    }
}
