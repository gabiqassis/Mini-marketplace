package br.com.marketplace.invoice.adapter.in.messaging;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.IssueInvoiceCommand;
import br.com.marketplace.contracts.Events.SellerProfileUpdated;
import br.com.marketplace.contracts.Queues;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.invoice.application.port.in.IssueInvoiceUseCase;
import br.com.marketplace.invoice.application.port.in.SellerProfileUseCase;
import br.com.marketplace.messaging.Envelopes;
import br.com.marketplace.messaging.LogContext;
import br.com.marketplace.messaging.rabbit.RetryingRabbitHandler;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Entradas: comando de emissao (RabbitMQ, com retry/parking) e dados do vendedor (Kafka). */
@Component
class InvoiceListeners {

    private final Envelopes envelopes;
    private final RetryingRabbitHandler rabbit;
    private final IssueInvoiceUseCase issue;
    private final SellerProfileUseCase sellers;

    InvoiceListeners(Envelopes envelopes, RetryingRabbitHandler rabbit, IssueInvoiceUseCase issue, SellerProfileUseCase sellers) {
        this.envelopes = envelopes;
        this.rabbit = rabbit;
        this.issue = issue;
        this.sellers = sellers;
    }

    @RabbitListener(queues = Queues.INVOICE_ISSUE, concurrency = "${marketplace.invoice.consumers:2-4}")
    void onIssueCommand(Message message) {
        rabbit.handle(Queues.INVOICE_ISSUE, message,
                env -> issue.issue(env, envelopes.payload(env, IssueInvoiceCommand.class)),
                (env, reason) -> issue.giveUp(env, envelopes.payload(env, IssueInvoiceCommand.class), reason));
    }

    @KafkaListener(topics = Topics.SELLER_PROFILE_UPDATED)
    void onSellerProfile(String json) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            sellers.onProfileUpdated(env, envelopes.payload(env, SellerProfileUpdated.class));
        }
    }
}
