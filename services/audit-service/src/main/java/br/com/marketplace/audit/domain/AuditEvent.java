package br.com.marketplace.audit.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Um fato registrado, com os indices que permitem rastrear de ponta a ponta:
 * correlacao (fluxo), pedido, vendedor e SKUs, mais a posicao exata no Kafka (topico/particao/offset).
 */
public record AuditEvent(
        UUID messageId,
        String topic,
        int partition,
        long offset,
        String type,
        String producer,
        String correlationId,
        UUID causationId,
        UUID orderId,
        String sellerId,
        List<String> skus,
        Instant occurredAt,
        String payloadJson) {}
