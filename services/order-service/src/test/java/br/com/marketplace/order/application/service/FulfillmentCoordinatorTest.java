package br.com.marketplace.order.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.InvoiceRejected;
import br.com.marketplace.contracts.Events.ShipmentStatusChanged;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.order.domain.Fulfillment;
import br.com.marketplace.order.application.port.out.FulfillmentRepository;
import br.com.marketplace.order.domain.FulfillmentStatus;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.domain.OrderItem;
import br.com.marketplace.order.application.port.out.OrderRepository;
import br.com.marketplace.order.domain.OrderStatus;
import br.com.marketplace.order.domain.ShippingAddress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FulfillmentCoordinatorTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final FulfillmentRepository fulfillments = mock(FulfillmentRepository.class);
    private final InboxRepository inbox = mock(InboxRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final FulfillmentCoordinator coordinator =
            new FulfillmentCoordinator(orders, fulfillments, inbox, outbox, new OrderEvents(outbox));

    private final UUID orderId = UUID.randomUUID();
    private final EventEnvelope env = new EventEnvelope(UUID.randomUUID(), "X", Instant.now(), orderId.toString(), null, "p", null);

    private Order order(OrderStatus status) {
        return new Order(orderId, "buyer", "b@x.com", new ShippingAddress("13010100", "Rua", "1", "Campinas", "SP"),
                status, new BigDecimal("10.00"), null, false, List.of(new OrderItem("A", "loja", "A", 1, BigDecimal.TEN)));
    }

    private Fulfillment part(FulfillmentStatus status) {
        return new Fulfillment(orderId, "loja", BigDecimal.TEN, status, null, null, null);
    }

    @BeforeEach
    void setUp() {
        when(inbox.markProcessed(any(), any())).thenReturn(true);
        when(orders.transition(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void lastInvoiceTriggersCaptureOnce() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));
        when(fulfillments.find(orderId, "loja")).thenReturn(Optional.of(part(FulfillmentStatus.AWAITING_INVOICE)));
        when(fulfillments.findByOrder(orderId)).thenReturn(List.of(part(FulfillmentStatus.INVOICED)));

        coordinator.onInvoiceIssued(env, new InvoiceIssued(orderId, "loja", UUID.randomUUID(), 1, "k", "p", BigDecimal.TEN));

        verify(outbox).publish(eq(Topics.PAYMENT_CAPTURE_REQUESTED), any(), any(), any());
        verify(orders).markCaptureRequested(orderId);
        verify(orders).transition(orderId, OrderStatus.CONFIRMED, OrderStatus.INVOICED, null);
    }

    @Test
    void rejectedInvoicePutsOrderOnHoldWithoutCapture() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));
        when(fulfillments.find(orderId, "loja")).thenReturn(Optional.of(part(FulfillmentStatus.AWAITING_INVOICE)));
        when(fulfillments.findByOrder(orderId)).thenReturn(List.of(part(FulfillmentStatus.INVOICE_FAILED)));

        coordinator.onInvoiceRejected(env, new InvoiceRejected(orderId, "loja", "203", "nao habilitado"));

        verify(orders).transition(orderId, OrderStatus.CONFIRMED, OrderStatus.ON_HOLD, "INVOICE_REJECTED_203");
        verify(outbox, never()).publish(eq(Topics.PAYMENT_CAPTURE_REQUESTED), any(), any(), any());
    }

    @Test
    void lostShipmentRefundsSellerPartWithoutRestock() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.IN_DELIVERY)));
        when(fulfillments.find(orderId, "loja")).thenReturn(Optional.of(part(FulfillmentStatus.IN_TRANSIT)));
        when(fulfillments.findByOrder(orderId)).thenReturn(List.of(part(FulfillmentStatus.REFUND_PENDING)));

        coordinator.onShipmentStatusChanged(env, shipment("LOST"));

        verify(outbox).publish(eq(Topics.PAYMENT_REFUND_REQUESTED), any(), any(), any());
        verify(outbox, never()).publish(eq(Topics.INVENTORY_RESTOCK_REQUESTED), any(), any(), any());
    }

    @Test
    void returnedShipmentRefundsAndRestocks() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.IN_DELIVERY)));
        when(fulfillments.find(orderId, "loja")).thenReturn(Optional.of(part(FulfillmentStatus.IN_TRANSIT)));
        when(fulfillments.findByOrder(orderId)).thenReturn(List.of(part(FulfillmentStatus.REFUND_PENDING)));

        coordinator.onShipmentStatusChanged(env, shipment("RETURNED"));

        verify(outbox).publish(eq(Topics.PAYMENT_REFUND_REQUESTED), any(), any(), any());
        verify(outbox).publish(eq(Topics.INVENTORY_RESTOCK_REQUESTED), any(), any(), any());
    }

    @Test
    void lateShipmentEventAfterDeliveryChangesNothing() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.DELIVERED)));
        when(fulfillments.find(orderId, "loja")).thenReturn(Optional.of(part(FulfillmentStatus.DELIVERED)));

        coordinator.onShipmentStatusChanged(env, shipment("IN_TRANSIT"));

        verify(fulfillments, never()).update(any());
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    void eventsForUnconfirmedOrdersAreIgnored() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.CANCELLED)));

        coordinator.onShipmentStatusChanged(env, shipment("DELIVERED"));

        verify(fulfillments, never()).find(any(), any());
    }

    private ShipmentStatusChanged shipment(String status) {
        return new ShipmentStatusChanged(orderId, "loja", UUID.randomUUID(), "TRK", status, "Campinas", Instant.now());
    }
}
