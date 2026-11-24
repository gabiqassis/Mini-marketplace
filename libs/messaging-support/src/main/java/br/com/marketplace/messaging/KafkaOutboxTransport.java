package br.com.marketplace.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;

/** Produtor idempotente (acks=all): so marca como publicado depois do ack do broker. */
public class KafkaOutboxTransport implements OutboxTransport {

    private final KafkaTemplate<String, String> kafka;

    public KafkaOutboxTransport(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public OutboxRepository.Destination destination() {
        return OutboxRepository.Destination.KAFKA;
    }

    @Override
    public void send(OutboxRepository.Row row) {
        try {
            kafka.send(row.topic(), row.key(), row.body()).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido ao publicar", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Falha ao publicar em " + row.topic(), e);
        }
    }
}
