package br.com.marketplace.messaging.rabbit;

import br.com.marketplace.messaging.Envelopes;
import br.com.marketplace.messaging.JitteredExponentialBackOff;
import br.com.marketplace.messaging.MessagingAutoConfiguration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/** So ativa nos servicos que trazem spring-boot-starter-amqp. */
@AutoConfiguration(after = {RabbitAutoConfiguration.class, MessagingAutoConfiguration.class})
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(RabbitTemplate.class)
public class RabbitMessagingAutoConfiguration {

    @Bean
    RabbitOutboxTransport rabbitOutboxTransport(RabbitTemplate rabbit) {
        return new RabbitOutboxTransport(rabbit);
    }

    @Bean
    RetryingRabbitHandler retryingRabbitHandler(
            RabbitTemplate rabbit,
            Envelopes envelopes,
            @Value("${marketplace.rabbit.max-attempts:5}") int maxAttempts,
            @Value("${marketplace.rabbit.initial-backoff-ms:1000}") long initialBackoffMs) {
        JitteredExponentialBackOff backOff = new JitteredExponentialBackOff(
                initialBackoffMs, 2.0, 30_000, maxAttempts, () -> ThreadLocalRandom.current().nextDouble());
        return new RetryingRabbitHandler(rabbit, envelopes, backOff, maxAttempts);
    }
}
