package br.com.marketplace.audit.application.port.in;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Porta de entrada: trilha de auditoria imutavel de pedidos, itens e correlacoes. */
public interface AuditUseCase {

    /** Linha do tempo ponta a ponta de um pedido: do lote do vendedor ate a entrega. */
    record OrderTrail(UUID orderId, String currentStatus, List<Step> timeline, Map<String, List<Step>> sellerSideBySku) {}

    record Step(String at, String topic, String type, String producer, String messageId, String causationId, String payload) {}

    boolean record(String topic, int partition, long offset, String json);

    OrderTrail orderTrail(UUID orderId);

    List<Step> itemTrail(String sku);

    List<Step> correlationTrail(String correlationId);
}
