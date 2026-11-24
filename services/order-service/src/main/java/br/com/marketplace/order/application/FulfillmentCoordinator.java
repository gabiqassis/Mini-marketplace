package br.com.marketplace.order.application;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CapturePaymentRequested;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.InvoiceRejected;
import br.com.marketplace.contracts.Events.OrderLine;
import br.com.marketplace.contracts.Events.PaymentRefunded;
import br.com.marketplace.contracts.Events.RefundPaymentRequested;
import br.com.marketplace.contracts.Events.RestockRequested;
import br.com.marketplace.contracts.Events.ShipmentStatusChanged;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.InvalidMessageException;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.order.domain.Fulfillment;
import br.com.marketplace.order.domain.FulfillmentRepository;
import br.com.marketplace.order.domain.FulfillmentStatus;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.domain.OrderProgress;
import br.com.marketplace.order.domain.OrderRepository;
import br.com.marketplace.order.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pos-venda: acompanha cada parte (vendedor) do pedido confirmado.
 *
 * <ul>
 *   <li>Todas as notas emitidas -> captura o pagamento (uma vez so).</li>
 *   <li>Nota rejeitada -> pedido ON_HOLD (vendedor precisa corrigir; notificacao avisa).</li>
 *   <li>Extravio -> estorno da parte do vendedor.</li>
 *   <li>Devolucao -> estorno + reposicao do estoque.</li>
 * </ul>
 */
@Service
public class FulfillmentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentCoordinator.class);

    private final OrderRepository orders;
    private final FulfillmentRepository fulfillments;
    private final InboxRepository inbox;
    private final OutboxRepository outbox;
    private final OrderEvents events;

    public FulfillmentCoordinator(
            OrderRepository orders, FulfillmentRepository fulfillments, InboxRepository inbox, OutboxRepository outbox, OrderEvents events) {
        this.orders = orders;
        this.fulfillments = fulfillments;
        this.inbox = inbox;
        this.outbox = outbox;
        this.events = events;
    }

    @Transactional
    public void onInvoiceIssued(EventEnvelope env, InvoiceIssued e) {
        if (!inbox.markProcessed(env.messageId(), "order-service:invoice-issued")) {
            return;
        }
        Order order = lockConfirmed(e.orderId());
        if (order == null) {
            return;
        }
        Fulfillment part = part(e.orderId(), e.sellerId());
        part.advanceTo(FulfillmentStatus.INVOICED, null)
                .ifPresent(next -> fulfillments.update(next.withInvoiceKey(e.accessKey())));
        Correlation corr = Correlation.from(env);
        List<FulfillmentStatus> statuses = statuses(order.id());
        if (!order.captureRequested() && OrderProgress.readyToCapture(statuses)) {
            outbox.publish(Topics.PAYMENT_CAPTURE_REQUESTED, order.id().toString(),
                    new CapturePaymentRequested(order.id(), order.total()), corr);
            orders.markCaptureRequested(order.id());
        }
        recompute(order, statuses, null, corr);
    }

    @Transactional
    public void onInvoiceRejected(EventEnvelope env, InvoiceRejected e) {
        if (!inbox.markProcessed(env.messageId(), "order-service:invoice-rejected")) {
            return;
        }
        Order order = lockConfirmed(e.orderId());
        if (order == null) {
            return;
        }
        part(e.orderId(), e.sellerId()).advanceTo(FulfillmentStatus.INVOICE_FAILED, "INVOICE_" + e.code())
                .ifPresent(fulfillments::update);
        recompute(order, statuses(order.id()), "INVOICE_REJECTED_" + e.code(), Correlation.from(env));
    }

    @Transactional
    public void onShipmentStatusChanged(EventEnvelope env, ShipmentStatusChanged e) {
        if (!inbox.markProcessed(env.messageId(), "order-service:shipment-status")) {
            return;
        }
        Order order = lockConfirmed(e.orderId());
        if (order == null) {
            return;
        }
        FulfillmentStatus target;
        try {
            target = FulfillmentStatus.fromShipment(e.status());
        } catch (IllegalArgumentException ex) {
            throw new InvalidMessageException(ex.getMessage());
        }
        Fulfillment current = part(e.orderId(), e.sellerId());
        String problem = target == FulfillmentStatus.REFUND_PENDING ? e.status() : null;
        Optional<Fulfillment> advanced = current.advanceTo(target, problem);
        if (advanced.isEmpty()) {
            log.info("Status de envio {} ignorado para pedido {} / {} (parte ja em {})",
                    e.status(), e.orderId(), e.sellerId(), current.status());
            return;
        }
        Fulfillment next = advanced.get().withTrackingCode(e.trackingCode());
        fulfillments.update(next);
        Correlation corr = Correlation.from(env);

        if (target == FulfillmentStatus.REFUND_PENDING) {
            outbox.publish(Topics.PAYMENT_REFUND_REQUESTED, order.id().toString(),
                    new RefundPaymentRequested(order.id(), e.sellerId(), next.amount(), "SHIPMENT_" + e.status()), corr);
            if ("RETURNED".equals(e.status())) {
                List<OrderLine> lines = order.items().stream().filter(i -> i.sellerId().equals(e.sellerId()))
                        .map(i -> new OrderLine(i.sku(), i.quantity())).toList();
                outbox.publish(Topics.INVENTORY_RESTOCK_REQUESTED, order.id().toString(),
                        new RestockRequested(order.id(), e.sellerId(), lines, "RETURNED_TO_SELLER"), corr);
            }
        }
        recompute(order, statuses(order.id()), problem, corr);
    }

    @Transactional
    public void onPaymentRefunded(EventEnvelope env, PaymentRefunded e) {
        if (!inbox.markProcessed(env.messageId(), "order-service:payment-refunded")) {
            return;
        }
        Order order = lockConfirmed(e.orderId());
        if (order == null) {
            return; // estorno de pedido cancelado (aprovacao tardia): nada a acompanhar
        }
        fulfillments.find(e.orderId(), e.reference())
                .flatMap(p -> p.advanceTo(FulfillmentStatus.REFUNDED, null))
                .ifPresent(fulfillments::update);
        recompute(order, statuses(order.id()), null, Correlation.from(env));
    }

    private Order lockConfirmed(UUID orderId) {
        Order order = orders.lockById(orderId).orElse(null);
        if (order == null || !order.status().isConfirmed()) {
            log.info("Evento de pos-venda para pedido {} ignorado (status {})", orderId, order == null ? null : order.status());
            return null;
        }
        return order;
    }

    private Fulfillment part(UUID orderId, String sellerId) {
        return fulfillments.find(orderId, sellerId)
                .orElseThrow(() -> new InvalidMessageException("pedido " + orderId + " nao tem parte do vendedor " + sellerId));
    }

    private List<FulfillmentStatus> statuses(UUID orderId) {
        return fulfillments.findByOrder(orderId).stream().map(Fulfillment::status).toList();
    }

    private void recompute(Order order, List<FulfillmentStatus> statuses, String reason, Correlation corr) {
        OrderStatus derived = OrderProgress.derive(statuses);
        if (derived != order.status() && order.status().canTransitionTo(derived)
                && orders.transition(order.id(), order.status(), derived, reason)) {
            events.statusChanged(order, derived, reason, corr);
        }
    }
}
