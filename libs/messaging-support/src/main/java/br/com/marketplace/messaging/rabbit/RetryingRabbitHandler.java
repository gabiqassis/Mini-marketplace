package br.com.marketplace.messaging.rabbit;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Queues;
import br.com.marketplace.messaging.Envelopes;
import br.com.marketplace.messaging.InvalidMessageException;
import br.com.marketplace.messaging.JitteredExponentialBackOff;
import br.com.marketplace.messaging.LogContext;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Politica de falha dos consumidores RabbitMQ (ADR-0012):
 *
 * <ol>
 *   <li>Sucesso: ack.</li>
 *   <li>Falha transitoria: republica em &lt;fila&gt;.retry com expiration = backoff com jitter. Quando
 *       expira, o DLX devolve para a fila original. Header x-attempt conta as tentativas.</li>
 *   <li>Falha permanente ({@link InvalidMessageException}) ou tentativas esgotadas: vai para
 *       &lt;fila&gt;.parking (inspecao humana) e o callback onExhausted avisa o negocio.</li>
 * </ol>
 *
 * Se ate o republish falhar, a excecao sobe, a mensagem volta para a fila e o x-delivery-limit da
 * fila quorum impede loop infinito.
 */
public class RetryingRabbitHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryingRabbitHandler.class);
    public static final String ATTEMPT_HEADER = "x-attempt";

    private final RabbitTemplate rabbit;
    private final Envelopes envelopes;
    private final JitteredExponentialBackOff backOff;
    private final int maxAttempts;

    public RabbitRetryPolicy policy() {
        return new RabbitRetryPolicy(maxAttempts);
    }

    public RetryingRabbitHandler(RabbitTemplate rabbit, Envelopes envelopes, JitteredExponentialBackOff backOff, int maxAttempts) {
        this.rabbit = rabbit;
        this.envelopes = envelopes;
        this.backOff = backOff;
        this.maxAttempts = maxAttempts;
    }

    public void handle(
            String queue, Message message, Consumer<EventEnvelope> handler, BiConsumer<EventEnvelope, String> onExhausted) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        int attempt = attemptOf(message);
        EventEnvelope envelope;
        try {
            envelope = envelopes.read(body);
        } catch (InvalidMessageException e) {
            log.error("Mensagem ilegivel em {}: {}", queue, e.getMessage());
            park(queue, message, attempt, e.getMessage());
            return;
        }
        try (var ignored = LogContext.of(envelope)) {
            try {
                handler.accept(envelope);
            } catch (RuntimeException e) {
                RabbitRetryPolicy.Decision decision = policy().decide(attempt, e);
                if (decision == RabbitRetryPolicy.Decision.RETRY) {
                    long delay = backOff.intervalFor(attempt);
                    log.warn("Falha transitoria em {} (tentativa {}/{}), nova tentativa em {} ms: {}",
                            queue, attempt + 1, maxAttempts, delay, e.getMessage());
                    retry(queue, message, attempt + 1, delay);
                } else {
                    log.error("Desistindo de {} apos {} tentativa(s): {}", queue, attempt + 1, e.getMessage());
                    park(queue, message, attempt, e.getMessage());
                    onExhausted.accept(envelope, e.getMessage());
                }
            }
        }
    }

    private static int attemptOf(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(ATTEMPT_HEADER);
        return value instanceof Number n ? n.intValue() : 0;
    }

    private void retry(String queue, Message original, int nextAttempt, long delayMs) {
        Message copy = new Message(original.getBody(), original.getMessageProperties());
        copy.getMessageProperties().setHeader(ATTEMPT_HEADER, nextAttempt);
        copy.getMessageProperties().setExpiration(Long.toString(delayMs));
        publishConfirmed(Queues.RETRY_EXCHANGE, Queues.retryOf(queue), copy);
    }

    private void park(String queue, Message original, int attempt, String reason) {
        Message copy = new Message(original.getBody(), original.getMessageProperties());
        copy.getMessageProperties().setHeader(ATTEMPT_HEADER, attempt);
        copy.getMessageProperties().setHeader("x-failure-reason", reason == null ? "" : reason);
        copy.getMessageProperties().setExpiration(null);
        publishConfirmed(Queues.PARKING_EXCHANGE, Queues.parkingOf(queue), copy);
    }

    private void publishConfirmed(String exchange, String routingKey, Message message) {
        rabbit.invoke(ops -> {
            ops.send(exchange, routingKey, message);
            ops.waitForConfirmsOrDie(5_000);
            return null;
        });
    }
}
