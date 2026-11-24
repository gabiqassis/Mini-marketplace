package br.com.marketplace.audit.application.service;

import br.com.marketplace.audit.domain.AuditEvent;
import br.com.marketplace.messaging.InvalidMessageException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Extrai os indices de rastreabilidade de QUALQUER envelope, sem conhecer cada tipo de evento:
 * procura orderId, sellerId e sku nos lugares convencionados (payload, lines[], sellers[].lines[]).
 * Evento novo no sistema ja aparece na auditoria sem mudar codigo aqui.
 */
public class AuditIndexer {

    private final ObjectMapper mapper;

    public AuditIndexer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AuditEvent index(String topic, int partition, long offset, String json) {
        JsonNode env;
        try {
            env = mapper.readTree(json);
        } catch (Exception e) {
            throw new InvalidMessageException("mensagem ilegivel no topico " + topic, e);
        }
        if (env == null || !env.hasNonNull("messageId") || !env.hasNonNull("payload")) {
            throw new InvalidMessageException("envelope sem messageId/payload no topico " + topic);
        }
        JsonNode payload = env.get("payload");
        Set<String> skus = new LinkedHashSet<>();
        collectSkus(payload, skus);
        return new AuditEvent(
                UUID.fromString(env.get("messageId").asText()),
                topic,
                partition,
                offset,
                text(env, "type"),
                text(env, "producer"),
                text(env, "correlationId"),
                uuid(text(env, "causationId")),
                uuid(text(payload, "orderId")),
                text(payload, "sellerId"),
                new ArrayList<>(skus),
                env.hasNonNull("occurredAt") ? Instant.parse(env.get("occurredAt").asText()) : Instant.now(),
                payload.toString());
    }

    private static void collectSkus(JsonNode node, Set<String> out) {
        if (node.hasNonNull("sku")) {
            out.add(node.get("sku").asText());
        }
        for (String arrayField : List.of("lines", "sellers", "items")) {
            JsonNode array = node.get(arrayField);
            if (array != null && array.isArray()) {
                array.forEach(child -> collectSkus(child, out));
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static UUID uuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
