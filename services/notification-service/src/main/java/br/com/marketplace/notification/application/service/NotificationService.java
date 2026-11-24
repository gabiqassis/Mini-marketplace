package br.com.marketplace.notification.application.service;

import br.com.marketplace.notification.application.port.in.NotificationUseCase;
import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.InvoiceRejected;
import br.com.marketplace.contracts.Events.OrderConfirmed;
import br.com.marketplace.contracts.Events.OrderStatusChanged;
import br.com.marketplace.contracts.Events.SellerProfileUpdated;
import br.com.marketplace.contracts.Events.SendNotificationJob;
import br.com.marketplace.contracts.Events.ShipmentStatusChanged;
import br.com.marketplace.contracts.Queues;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.notification.domain.Draft;
import br.com.marketplace.notification.domain.Notification;
import br.com.marketplace.notification.domain.NotificationPlanner;
import br.com.marketplace.notification.application.port.out.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka (fato) -&gt; notificacao gravada + job no RabbitMQ (outbox, mesma transacao).
 * Por que dois brokers aqui: o fato e consumido por varios servicos (Kafka); o ENVIO e trabalho
 * lento, com retry e limite de vazao do provedor de e-mail (fila RabbitMQ).
 */
@Service
public class NotificationService implements NotificationUseCase {

    private final NotificationPlanner planner = new NotificationPlanner();
    private final NotificationRepository notifications;
    private final InboxRepository inbox;
    private final OutboxRepository outbox;

    public NotificationService(NotificationRepository notifications, InboxRepository inbox, OutboxRepository outbox) {
        this.notifications = notifications;
        this.inbox = inbox;
        this.outbox = outbox;
    }

    @Transactional
    @Override
    public void onSellerProfile(EventEnvelope env, SellerProfileUpdated e) {
        if (inbox.markProcessed(env.messageId(), "notification-service:seller-profile")) {
            notifications.saveSellerEmail(e.sellerId(), e.email());
        }
    }

    @Transactional
    @Override
    public void onOrderConfirmed(EventEnvelope env, OrderConfirmed e) {
        if (!inbox.markProcessed(env.messageId(), "notification-service:order-confirmed")) {
            return;
        }
        notifications.saveBuyerEmail(e.orderId(), e.buyerId(), e.buyerEmail());
        List<NotificationPlanner.SellerSale> sales = e.sellers().stream()
                .map(p -> new NotificationPlanner.SellerSale(p.sellerId(), p.amount(),
                        p.lines().stream().map(l -> new NotificationPlanner.SoldItem(l.title(), l.quantity())).toList()))
                .toList();
        create(env, e.orderId(), planner.orderConfirmed(e.orderId(), e.buyerId(), e.total(), sales), e.buyerEmail());
    }

    @Transactional
    @Override
    public void onOrderStatusChanged(EventEnvelope env, OrderStatusChanged e) {
        if (inbox.markProcessed(env.messageId(), "notification-service:order-status")) {
            create(env, e.orderId(), planner.orderStatusChanged(e.orderId(), e.buyerId(), e.status(), e.reason()), e.buyerEmail());
        }
    }

    @Transactional
    @Override
    public void onInvoiceIssued(EventEnvelope env, InvoiceIssued e) {
        if (inbox.markProcessed(env.messageId(), "notification-service:invoice-issued")) {
            create(env, e.orderId(), planner.invoiceIssued(e.orderId(), e.sellerId(), e.number(), e.accessKey()), null);
        }
    }

    @Transactional
    @Override
    public void onInvoiceRejected(EventEnvelope env, InvoiceRejected e) {
        if (inbox.markProcessed(env.messageId(), "notification-service:invoice-rejected")) {
            create(env, e.orderId(), planner.invoiceRejected(e.orderId(), e.sellerId(), e.code(), e.reason()), null);
        }
    }

    @Transactional
    @Override
    public void onShipmentStatusChanged(EventEnvelope env, ShipmentStatusChanged e) {
        if (inbox.markProcessed(env.messageId(), "notification-service:shipment-status")) {
            create(env, e.orderId(), planner.shipmentStatusChanged(e.orderId(), notifications.buyerId(e.orderId()).orElse("buyer"), e.sellerId(), e.status(),
                    e.trackingCode(), e.location()), null);
        }
    }

    private void create(EventEnvelope env, UUID orderId, List<Draft> drafts, String knownBuyerEmail) {
        Correlation corr = Correlation.from(env);
        for (Draft d : drafts) {
            String email = resolveEmail(d, orderId, knownBuyerEmail);
            Notification n = new Notification(UUID.randomUUID(), orderId, d.recipient(), d.recipientId(), email, d.template(),
                    d.subject(), d.body(), Notification.Status.PENDING);
            String dedupeKey = env.messageId() + ":" + d.recipient() + ":" + d.recipientId() + ":" + d.template();
            if (notifications.insertIfAbsent(n, dedupeKey)) {
                outbox.send(Queues.NOTIFICATION_SEND, n.id().toString(), new SendNotificationJob(n.id()), corr);
            }
        }
    }

    /** Contato ainda nao chegou (evento fora de ordem)? Excecao = retry com backoff ate chegar. */
    private String resolveEmail(Draft d, UUID orderId, String knownBuyerEmail) {
        if (d.recipient() == Draft.Recipient.SELLER) {
            return notifications.sellerEmail(d.recipientId())
                    .orElseThrow(() -> new IllegalStateException("e-mail do vendedor " + d.recipientId() + " ainda desconhecido"));
        }
        if (knownBuyerEmail != null) {
            return knownBuyerEmail;
        }
        return notifications.buyerEmail(orderId)
                .orElseThrow(() -> new IllegalStateException("e-mail do comprador do pedido " + orderId + " ainda desconhecido"));
    }
}
