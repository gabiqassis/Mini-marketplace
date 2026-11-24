package br.com.marketplace.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.PaymentAuthorized;
import br.com.marketplace.contracts.Events.PaymentDeclined;
import br.com.marketplace.contracts.Events.RefundPaymentRequested;
import br.com.marketplace.contracts.Queues;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.order.domain.FulfillmentRepository;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.domain.OrderItem;
import br.com.marketplace.order.domain.OrderRepository;
import br.com.marketplace.order.domain.OrderStatus;
import br.com.marketplace.order.domain.ShippingAddress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SagaCoordinatorTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final FulfillmentRepository fulfillments = mock(FulfillmentRepository.class);
    private final InboxRepository inbox = mock(InboxRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final SagaCoordinator saga = new SagaCoordinator(orders, fulfillments, inbox, outbox, new OrderEvents(outbox));

    private final UUID orderId = UUID.randomUUID();
    private final EventEnvelope env = new EventEnvelope(UUID.randomUUID(), "X", Instant.now(), orderId.toString(), null, "p", null);

    private Order order(OrderStatus status) {
        return new Order(orderId, "buyer", "b@x.com", new ShippingAddress("13010100", "Rua", "1", "Campinas", "SP"),
                status, new BigDecimal("30.00"), null, false, List.of(
                        new OrderItem("A", "loja-1", "A", 1, new BigDecimal("10.00")),
                        new OrderItem("B", "loja-2", "B", 1, new BigDecimal("20.00"))));
    }

    @BeforeEach
    void firstDelivery() {
        when(inbox.markProcessed(any(), any())).thenReturn(true);
        when(orders.transition(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void authorizedPaymentConfirmsAndOrdersOneInvoicePerSeller() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.AWAITING_PAYMENT)));

        saga.onPaymentAuthorized(env, new PaymentAuthorized(orderId, UUID.randomUUID(), new BigDecimal("30.00")));

        verify(outbox).publish(eq(Topics.INVENTORY_COMMIT_REQUESTED), any(), any(), any());
        verify(outbox, times(2)).send(eq(Queues.INVOICE_ISSUE), any(), any(), any());
        verify(outbox).publish(eq(Topics.ORDER_CONFIRMED), any(), any(), any());
        verify(fulfillments).insertAll(anyList());
    }

    @Test
    void lateAuthorizationAfterCancelIsRefundedAutomatically() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.CANCELLED)));

        saga.onPaymentAuthorized(env, new PaymentAuthorized(orderId, UUID.randomUUID(), new BigDecimal("30.00")));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(Topics.PAYMENT_REFUND_REQUESTED), any(), payload.capture(), any());
        assertThat(((RefundPaymentRequested) payload.getValue()).reason()).isEqualTo("LATE_AUTHORIZATION_AFTER_CANCEL");
        verify(outbox, never()).send(any(), any(), any(), any());
    }

    @Test
    void declinedPaymentStartsCompensation() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.AWAITING_PAYMENT)));

        saga.onPaymentDeclined(env, new PaymentDeclined(orderId, "LIMIT_EXCEEDED"));

        verify(orders).transition(orderId, OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLING, "PAYMENT_LIMIT_EXCEEDED");
        verify(outbox).publish(eq(Topics.INVENTORY_RELEASE_REQUESTED), any(), any(), any());
    }

    @Test
    void outOfOrderEventDoesNothingWhenGuardFails() {
        when(orders.lockById(orderId)).thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));
        when(orders.transition(orderId, OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLING, "PAYMENT_X")).thenReturn(false);

        saga.onPaymentDeclined(env, new PaymentDeclined(orderId, "X"));

        verify(outbox, never()).publish(eq(Topics.INVENTORY_RELEASE_REQUESTED), any(), any(), any());
    }

    @Test
    void repeatedMessageIsIgnored() {
        when(inbox.markProcessed(any(), any())).thenReturn(false);

        saga.onPaymentAuthorized(env, new PaymentAuthorized(orderId, UUID.randomUUID(), BigDecimal.ONE));

        verify(orders, never()).lockById(any());
    }
}
