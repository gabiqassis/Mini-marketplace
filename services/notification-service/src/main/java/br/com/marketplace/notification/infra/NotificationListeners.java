package br.com.marketplace.notification.infra;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.InvoiceRejected;
import br.com.marketplace.contracts.Events.OrderConfirmed;
import br.com.marketplace.contracts.Events.OrderStatusChanged;
import br.com.marketplace.contracts.Events.SellerProfileUpdated;
import br.com.marketplace.contracts.Events.SendNotificationJob;
import br.com.marketplace.contracts.Events.ShipmentStatusChanged;
import br.com.marketplace.contracts.Queues;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Envelopes;
import br.com.marketplace.messaging.LogContext;
import br.com.marketplace.messaging.rabbit.RetryingRabbitHandler;
import br.com.marketplace.notification.application.NotificationService;
import br.com.marketplace.notification.application.SendNotificationWorker;
import java.util.function.BiConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class NotificationListeners {

    private final Envelopes envelopes;
    private final NotificationService notifications;
    private final SendNotificationWorker worker;
    private final RetryingRabbitHandler rabbit;

    NotificationListeners(
            Envelopes envelopes, NotificationService notifications, SendNotificationWorker worker, RetryingRabbitHandler rabbit) {
        this.envelopes = envelopes;
        this.notifications = notifications;
        this.worker = worker;
        this.rabbit = rabbit;
    }

    private <T> void dispatch(String json, Class<T> type, BiConsumer<EventEnvelope, T> handler) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            handler.accept(env, envelopes.payload(env, type));
        }
    }

    @KafkaListener(topics = Topics.SELLER_PROFILE_UPDATED)
    void onSellerProfile(String json) {
        dispatch(json, SellerProfileUpdated.class, notifications::onSellerProfile);
    }

    @KafkaListener(topics = Topics.ORDER_CONFIRMED)
    void onOrderConfirmed(String json) {
        dispatch(json, OrderConfirmed.class, notifications::onOrderConfirmed);
    }

    @KafkaListener(topics = Topics.ORDER_STATUS_CHANGED)
    void onOrderStatus(String json) {
        dispatch(json, OrderStatusChanged.class, notifications::onOrderStatusChanged);
    }

    @KafkaListener(topics = Topics.INVOICE_ISSUED)
    void onInvoiceIssued(String json) {
        dispatch(json, InvoiceIssued.class, notifications::onInvoiceIssued);
    }

    @KafkaListener(topics = Topics.INVOICE_REJECTED)
    void onInvoiceRejected(String json) {
        dispatch(json, InvoiceRejected.class, notifications::onInvoiceRejected);
    }

    @KafkaListener(topics = Topics.SHIPMENT_STATUS_CHANGED)
    void onShipmentStatus(String json) {
        dispatch(json, ShipmentStatusChanged.class, notifications::onShipmentStatusChanged);
    }

    @RabbitListener(queues = Queues.NOTIFICATION_SEND, concurrency = "${marketplace.notification.consumers:2-4}")
    void onSendJob(Message message) {
        rabbit.handle(Queues.NOTIFICATION_SEND, message,
                env -> worker.send(envelopes.payload(env, SendNotificationJob.class).notificationId()),
                (env, reason) -> worker.giveUp(envelopes.payload(env, SendNotificationJob.class).notificationId(), reason));
    }
}
