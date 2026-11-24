package br.com.marketplace.messaging;

import br.com.marketplace.contracts.EventEnvelope;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Outbox transacional para os DOIS brokers. Chame SEMPRE dentro da transacao do dado de negocio:
 * ou grava o dado e a mensagem, ou nenhum dos dois (sem dual-write).
 */
public class OutboxRepository {

    public enum Destination {
        KAFKA,
        RABBIT
    }

    public record Row(long seq, Destination destination, String topic, String key, String body) {}

    private final JdbcClient jdbc;
    private final Envelopes envelopes;

    public OutboxRepository(JdbcClient jdbc, Envelopes envelopes) {
        this.jdbc = jdbc;
        this.envelopes = envelopes;
    }

    /** Fato para o Kafka. key define a particao (ordem garantida por chave). */
    public UUID publish(String topic, String key, Object payload, Correlation correlation) {
        return append(Destination.KAFKA, topic, key, payload, correlation);
    }

    /** Comando/job para o RabbitMQ. topic = routing key (= nome da fila). */
    public UUID send(String routingKey, String key, Object payload, Correlation correlation) {
        return append(Destination.RABBIT, routingKey, key, payload, correlation);
    }

    private UUID append(Destination destination, String topic, String key, Object payload, Correlation correlation) {
        EventEnvelope env = envelopes.wrap(payload, correlation);
        jdbc.sql("""
                        INSERT INTO outbox (id, destination, topic, msg_key, body)
                        VALUES (:id, :dest, :topic, :key, CAST(:body AS jsonb))
                        """)
                .param("id", env.messageId())
                .param("dest", destination.name())
                .param("topic", topic)
                .param("key", key)
                .param("body", envelopes.write(env))
                .update();
        return env.messageId();
    }

    /** SKIP LOCKED: varias instancias do relay nao disputam as mesmas linhas. */
    List<Row> lockPending(int limit) {
        return jdbc.sql("""
                        SELECT seq, destination, topic, msg_key, body::text AS body
                          FROM outbox
                         WHERE published_at IS NULL
                         ORDER BY seq
                         LIMIT :limit
                           FOR UPDATE SKIP LOCKED
                        """)
                .param("limit", limit)
                .query((rs, n) -> new Row(
                        rs.getLong("seq"),
                        Destination.valueOf(rs.getString("destination")),
                        rs.getString("topic"),
                        rs.getString("msg_key"),
                        rs.getString("body")))
                .list();
    }

    void markPublished(long seq) {
        jdbc.sql("UPDATE outbox SET published_at = now() WHERE seq = :seq").param("seq", seq).update();
    }

    int purgePublishedOlderThanDays(int days) {
        return jdbc.sql("DELETE FROM outbox WHERE published_at IS NOT NULL AND published_at < now() - make_interval(days => :d)")
                .param("d", days)
                .update();
    }
}
