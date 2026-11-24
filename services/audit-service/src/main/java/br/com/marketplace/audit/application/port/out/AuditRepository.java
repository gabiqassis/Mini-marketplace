package br.com.marketplace.audit.application.port.out;

import br.com.marketplace.audit.domain.AuditEvent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Porta de saida: trilha append-only (trigger no banco proibe UPDATE/DELETE). */
public interface AuditRepository {

    /** ON CONFLICT (message_id) DO NOTHING: reentrega do Kafka nao duplica a trilha. */
    boolean append(AuditEvent event);

    List<AuditEvent> byOrder(UUID orderId);

    List<AuditEvent> bySkus(Collection<String> skus);

    List<AuditEvent> byCorrelation(String correlationId);
}
