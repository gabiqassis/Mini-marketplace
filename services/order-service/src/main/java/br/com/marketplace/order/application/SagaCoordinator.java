package br.com.marketplace.order.application;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.Address;
import br.com.marketplace.contracts.Events.AuthorizePaymentRequested;
import br.com.marketplace.contracts.Events.CommitStockRequested;
import br.com.marketplace.contracts.Events.IssueInvoiceCommand;
import br.com.marketplace.contracts.Events.OrderConfirmed;
import br.com.marketplace.contracts.Events.PaymentAuthorized;
import br.com.marketplace.contracts.Events.PaymentDeclined;
import br.com.marketplace.contracts.Events.RefundPaymentRequested;
import br.com.marketplace.contracts.Events.ReleaseStockRequested;
import br.com.marketplace.contracts.Events.SellerPart;
import br.com.marketplace.contracts.Events.SoldLine;
import br.com.marketplace.contracts.Events.StockReleased;
import br.com.marketplace.contracts.Events.StockReservationRejected;
import br.com.marketplace.contracts.Events.StockReserved;
import br.com.marketplace.contracts.Queues;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.order.domain.Fulfillment;
import br.com.marketplace.order.domain.FulfillmentRepository;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.domain.OrderItem;
import br.com.marketplace.order.domain.OrderRepository;
import br.com.marketplace.order.domain.OrderStatus;
import br.com.marketplace.order.domain.ShippingAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestrador da SAGA de compra (ADR-0004). Cada handler:
 * 1) inbox (mensagem repetida nao faz nada)
 * 2) transicao GUARDADA (evento atrasado/fora de ordem nao faz nada)
 * 3) proximos comandos gravados no outbox na MESMA transacao.
 */
@Service
public class SagaCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SagaCoordinator.class);
    private static final int SWEEP_BATCH = 50;

    private final OrderRepository orders;
    private final FulfillmentRepository fulfillments;
    private final InboxRepository inbox;
    private final OutboxRepository outbox;
    private final OrderEvents events;

    public SagaCoordinator(
            OrderRepository orders, FulfillmentRepository fulfillments, InboxRepository inbox, OutboxRepository outbox, OrderEvents events) {
        this.orders = orders;
        this.fulfillments = fulfillments;
        this.inbox = inbox;
        this.outbox = outbox;
        this.events = events;
    }

    /** Estoque reservado -> pede AUTORIZACAO (nao captura: so captura com as notas emitidas). */
    @Transactional
    public void onStockReserved(EventEnvelope env, StockReserved e) {
        if (!inbox.markProcessed(env.messageId(), "order-service:stock-reserved")) {
            return;
        }
        Order order = orders.lockById(e.orderId()).orElse(null);
        if (order != null && orders.transition(order.id(), OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT, null)) {
            Correlation corr = Correlation.from(env);
            outbox.publish(Topics.PAYMENT_AUTHORIZE_REQUESTED, order.id().toString(),
                    new AuthorizePaymentRequested(order.id(), order.buyerId(), order.total()), corr);
            events.statusChanged(order, OrderStatus.AWAITING_PAYMENT, null, corr);
        }
    }

    /** Sem estoque (ou corrida pela ultima unidade perdida): nada foi reservado, cancela direto. */
    @Transactional
    public void onReservationRejected(EventEnvelope env, StockReservationRejected e) {
        if (!inbox.markProcessed(env.messageId(), "order-service:reservation-rejected")) {
            return;
        }
        Order order = orders.lockById(e.orderId()).orElse(null);
        if (order != null && orders.transition(order.id(), OrderStatus.PENDING, OrderStatus.CANCELLED, e.reason())) {
            events.statusChanged(order, OrderStatus.CANCELLED, e.reason(), Correlation.from(env));
        }
    }

    /**
     * Pagamento autorizado -> CONFIRMA: baixa estoque, cria uma parte por vendedor e manda emitir as
     * notas (RabbitMQ). Se o pedido ja foi cancelado (aprovacao TARDIA, depois do timeout), estorna.
     */
    @Transactional
    public void onPaymentAuthorized(EventEnvelope env, PaymentAuthorized e) {
        if (!inbox.markProcessed(env.messageId(), "order-service:payment-authorized")) {
            return;
        }
        Order order = orders.lockById(e.orderId()).orElse(null);
        if (order == null) {
            return;
        }
        Correlation corr = Correlation.from(env);
        if (order.status().isCancelled()) {
            log.warn("Pagamento do pedido {} aprovado depois do cancelamento: estornando", order.id());
            outbox.publish(Topics.PAYMENT_REFUND_REQUESTED, order.id().toString(),
                    new RefundPaymentRequested(order.id(), "order", e.amount(), "LATE_AUTHORIZATION_AFTER_CANCEL"), corr);
            return;
        }
        if (!orders.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED, null)) {
            return;
        }
        outbox.publish(Topics.INVENTORY_COMMIT_REQUESTED, order.id().toString(), new CommitStockRequested(order.id()), corr);

        Address address = toAddress(order.address());
        List<SellerPart> parts = new ArrayList<>();
        List<Fulfillment> created = new ArrayList<>();
        for (Map.Entry<String, List<OrderItem>> entry : order.itemsBySeller().entrySet()) {
            String sellerId = entry.getKey();
            List<SoldLine> lines = entry.getValue().stream()
                    .map(i -> new SoldLine(i.sku(), i.title(), i.quantity(), i.unitPrice())).toList();
            parts.add(new SellerPart(sellerId, order.amountOf(sellerId), lines));
            created.add(Fulfillment.awaitingInvoice(order.id(), sellerId, order.amountOf(sellerId)));
            outbox.send(Queues.INVOICE_ISSUE, order.id() + ":" + sellerId,
                    new IssueInvoiceCommand(order.id(), sellerId, order.buyerId(), address, order.amountOf(sellerId), lines), corr);
        }
        fulfillments.insertAll(created);
        outbox.publish(Topics.ORDER_CONFIRMED, order.id().toString(),
                new OrderConfirmed(order.id(), order.buyerId(), order.buyerEmail(), address, order.total(), parts), corr);
        events.statusChanged(order, OrderStatus.CONFIRMED, null, corr);
    }

    /** Pagamento recusado -> COMPENSACAO: libera a reserva. */
    @Transactional
    public void onPaymentDeclined(EventEnvelope env, PaymentDeclined e) {
        if (!inbox.markProcessed(env.messageId(), "order-service:payment-declined")) {
            return;
        }
        Order order = orders.lockById(e.orderId()).orElse(null);
        if (order != null && orders.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLING, "PAYMENT_" + e.reason())) {
            startCompensation(order, "PAYMENT_" + e.reason(), Correlation.from(env));
        }
    }

    @Transactional
    public void onStockReleased(EventEnvelope env, StockReleased e) {
        if (!inbox.markProcessed(env.messageId(), "order-service:stock-released")) {
            return;
        }
        Order order = orders.lockById(e.orderId()).orElse(null);
        if (order != null && orders.transition(order.id(), OrderStatus.CANCELLING, OrderStatus.CANCELLED, null)) {
            events.statusChanged(order, OrderStatus.CANCELLED, order.cancelReason(), Correlation.from(env));
        }
    }

    /** Pedidos presos alem do prazo entram em compensacao (resposta perdida, servico fora do ar). */
    @Transactional
    public int cancelStale(Duration pendingTimeout, Duration paymentTimeout) {
        int count = 0;
        count += cancel(OrderStatus.PENDING, pendingTimeout, "TIMEOUT_RESERVATION");
        count += cancel(OrderStatus.AWAITING_PAYMENT, paymentTimeout, "TIMEOUT_PAYMENT");
        return count;
    }

    private int cancel(OrderStatus from, Duration timeout, String reason) {
        int count = 0;
        for (UUID id : orders.findStaleIds(from, Instant.now().minus(timeout), SWEEP_BATCH)) {
            Order order = orders.lockById(id).orElse(null);
            if (order != null && orders.transition(id, from, OrderStatus.CANCELLING, reason)) {
                startCompensation(order, reason, Correlation.root(id));
                count++;
            }
        }
        return count;
    }

    private void startCompensation(Order order, String reason, Correlation corr) {
        outbox.publish(Topics.INVENTORY_RELEASE_REQUESTED, order.id().toString(), new ReleaseStockRequested(order.id(), reason), corr);
        events.statusChanged(order, OrderStatus.CANCELLING, reason, corr);
    }

    static Address toAddress(ShippingAddress a) {
        return new Address(a.zipCode(), a.street(), a.number(), a.city(), a.state());
    }
}
