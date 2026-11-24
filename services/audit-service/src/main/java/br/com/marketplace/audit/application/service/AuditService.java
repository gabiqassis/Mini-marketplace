package br.com.marketplace.audit.application.service;

import br.com.marketplace.audit.application.port.in.AuditUseCase;
import br.com.marketplace.audit.application.port.in.AuditUseCase.OrderTrail;
import br.com.marketplace.audit.application.port.in.AuditUseCase.Step;
import br.com.marketplace.audit.domain.AuditEvent;
import br.com.marketplace.audit.application.port.out.AuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService implements AuditUseCase {

    private final AuditRepository repository;
    private final AuditIndexer indexer;
    private final ObjectMapper mapper;

    public AuditService(AuditRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.indexer = new AuditIndexer(mapper);
        this.mapper = mapper;
    }

    @Override
    public boolean record(String topic, int partition, long offset, String json) {
        return repository.append(indexer.index(topic, partition, offset, json));
    }

    @Override
    public OrderTrail orderTrail(UUID orderId) {
        List<AuditEvent> orderEvents = repository.byOrder(orderId);
        Set<String> skus = new LinkedHashSet<>();
        orderEvents.forEach(e -> skus.addAll(e.skus()));

        Map<String, List<Step>> sellerSide = new LinkedHashMap<>();
        if (!skus.isEmpty()) {
            for (AuditEvent e : repository.bySkus(skus)) {
                if (e.orderId() == null) {
                    for (String sku : e.skus()) {
                        if (skus.contains(sku)) {
                            sellerSide.computeIfAbsent(sku, k -> new java.util.ArrayList<>()).add(step(e));
                        }
                    }
                }
            }
        }
        String status = orderEvents.stream()
                .filter(e -> "OrderStatusChanged".equals(e.type()))
                .max(Comparator.comparing(AuditEvent::occurredAt))
                .map(e -> statusOf(e.payloadJson()))
                .orElse(null);
        return new OrderTrail(orderId, status, orderEvents.stream().map(AuditService::step).toList(), sellerSide);
    }

    private String statusOf(String payloadJson) {
        try {
            return mapper.readTree(payloadJson).path("status").asText(null);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public List<Step> itemTrail(String sku) {
        return repository.bySkus(List.of(sku)).stream().map(AuditService::step).toList();
    }

    @Override
    public List<Step> correlationTrail(String correlationId) {
        return repository.byCorrelation(correlationId).stream().map(AuditService::step).toList();
    }

    private static Step step(AuditEvent e) {
        return new Step(e.occurredAt().toString(), e.topic(), e.type(), e.producer(), e.messageId().toString(),
                e.causationId() == null ? null : e.causationId().toString(), e.payloadJson());
    }
}
