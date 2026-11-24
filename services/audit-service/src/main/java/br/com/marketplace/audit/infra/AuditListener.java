package br.com.marketplace.audit.infra;

import br.com.marketplace.audit.application.AuditService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Assina TODOS os topicos de negocio por padrao (novos topicos entram sozinhos). DLTs ficam de fora:
 * elas sao monitoradas por alerta, nao fazem parte da historia do pedido.
 */
@Component
class AuditListener {

    private final AuditService audit;

    AuditListener(AuditService audit) {
        this.audit = audit;
    }

    @KafkaListener(topicPattern = "^(seller|catalog|inventory|payment|order|invoice|shipping)\\.[a-z-]+\\.v\\d+$")
    void onAnyFact(ConsumerRecord<String, String> record) {
        audit.record(record.topic(), record.partition(), record.offset(), record.value());
    }
}
