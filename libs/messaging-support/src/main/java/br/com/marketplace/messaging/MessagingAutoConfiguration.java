package br.com.marketplace.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfiguration(after = {
    JacksonAutoConfiguration.class,
    JdbcClientAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    TransactionAutoConfiguration.class
})
@EnableScheduling
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Envelopes envelopes(ObjectMapper mapper, @Value("${spring.application.name:unknown}") String producer, Clock clock) {
        return new Envelopes(mapper, producer, clock);
    }

    @Bean
    OutboxRepository outboxRepository(JdbcClient jdbc, Envelopes envelopes) {
        return new OutboxRepository(jdbc, envelopes);
    }

    @Bean
    InboxRepository inboxRepository(JdbcClient jdbc) {
        return new InboxRepository(jdbc);
    }

    @Bean
    KafkaOutboxTransport kafkaOutboxTransport(KafkaTemplate<String, String> kafka) {
        return new KafkaOutboxTransport(kafka);
    }

    @Bean
    OutboxRelay outboxRelay(
            OutboxRepository outbox,
            List<OutboxTransport> transports,
            PlatformTransactionManager txManager,
            @Value("${marketplace.outbox.batch-size:100}") int batchSize) {
        return new OutboxRelay(outbox, transports, new TransactionTemplate(txManager), batchSize);
    }

    /**
     * Boot lento (varias JVMs no mesmo notebook) nao pode derrubar o consumidor: espera ate 2 min a
     * thread do consumidor subir (padrao do Spring Kafka: 30 s).
     */
    @Bean
    ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>> slowStartTolerantContainers() {
        return container -> container.getContainerProperties().setConsumerStartTimeout(Duration.ofMinutes(2));
    }

    /**
     * Resiliencia do consumidor Kafka: 4 retentativas com backoff exponencial COM JITTER e depois a
     * mensagem vai para &lt;topico&gt;.DLT (mesma particao). Mensagem invalida vai direto para a DLT:
     * um "poison pill" nunca trava a particao.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        JitteredExponentialBackOff backOff = new JitteredExponentialBackOff(
                500, 2.0, 4_000, 4, () -> ThreadLocalRandom.current().nextDouble());
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafka, (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidMessageException.class, JsonProcessingException.class);
        return handler;
    }
}
