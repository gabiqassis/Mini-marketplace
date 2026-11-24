package br.com.marketplace.messaging;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Thread agendada que publica o outbox (Kafka e RabbitMQ). Entrega at-least-once: se cair depois de
 * enviar e antes de marcar, reenvia - por isso todo consumidor tem inbox. Se um broker estiver fora,
 * a linha fica pendente e sai no proximo ciclo: o servico continua aceitando trabalho.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final Map<OutboxRepository.Destination, OutboxTransport> transports;
    private final TransactionTemplate tx;
    private final int batchSize;

    public OutboxRelay(OutboxRepository outbox, List<OutboxTransport> transports, TransactionTemplate tx, int batchSize) {
        this.outbox = outbox;
        this.transports = new EnumMap<>(OutboxRepository.Destination.class);
        transports.forEach(t -> this.transports.put(t.destination(), t));
        this.tx = tx;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${marketplace.outbox.poll-ms:200}")
    public void relay() {
        try {
            tx.executeWithoutResult(status -> {
                for (OutboxRepository.Row row : outbox.lockPending(batchSize)) {
                    transportFor(row).send(row);
                    outbox.markPublished(row.seq());
                }
            });
        } catch (RuntimeException e) {
            // Broker fora do ar? A transacao desfaz e tentamos no proximo ciclo. Nada se perde.
            log.warn("Relay do outbox falhou, tentara de novo: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${marketplace.outbox.purge-cron:0 15 3 * * *}")
    public void purge() {
        int n = outbox.purgePublishedOlderThanDays(7);
        if (n > 0) {
            log.info("Outbox: {} linhas antigas removidas", n);
        }
    }

    private OutboxTransport transportFor(OutboxRepository.Row row) {
        OutboxTransport transport = transports.get(row.destination());
        if (transport == null) {
            throw new IllegalStateException("Sem transporte para " + row.destination() + " neste servico");
        }
        return transport;
    }
}
