package br.com.marketplace.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Envelope de TODA mensagem (Kafka, RabbitMQ e SQS).
 *
 * <ul>
 *   <li>{@code messageId}: chave de idempotencia (inbox do consumidor).</li>
 *   <li>{@code correlationId}: fio que liga tudo de um mesmo fluxo. No fluxo de compra e o orderId;
 *       no fluxo do vendedor e o batchId. E o que o audit-service e os logs usam para rastrear.</li>
 *   <li>{@code causationId}: messageId da mensagem que causou esta (arvore de causa e efeito).</li>
 *   <li>{@code producer}: servico que publicou.</li>
 * </ul>
 */
public record EventEnvelope(
        UUID messageId,
        String type,
        Instant occurredAt,
        String correlationId,
        UUID causationId,
        String producer,
        JsonNode payload) {}
