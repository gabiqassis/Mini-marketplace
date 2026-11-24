package br.com.marketplace.messaging.rabbit;

import br.com.marketplace.contracts.Queues;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.messaging.OutboxTransport;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Publica comandos no exchange marketplace.commands com publisher confirms: so marca o outbox como
 * publicado depois que o broker confirmou que gravou (mensagem persistente em fila quorum).
 */
public class RabbitOutboxTransport implements OutboxTransport {

    private final RabbitTemplate rabbit;

    public RabbitOutboxTransport(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public OutboxRepository.Destination destination() {
        return OutboxRepository.Destination.RABBIT;
    }

    @Override
    public void send(OutboxRepository.Row row) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(row.key());
        Message message = new Message(row.body().getBytes(StandardCharsets.UTF_8), props);
        rabbit.invoke(ops -> {
            ops.send(Queues.COMMANDS_EXCHANGE, row.topic(), message);
            ops.waitForConfirmsOrDie(5_000);
            return null;
        });
    }
}
