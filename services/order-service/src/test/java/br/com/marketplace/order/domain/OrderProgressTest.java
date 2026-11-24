package br.com.marketplace.order.domain;

import static br.com.marketplace.order.domain.FulfillmentStatus.AWAITING_INVOICE;
import static br.com.marketplace.order.domain.FulfillmentStatus.DELIVERED;
import static br.com.marketplace.order.domain.FulfillmentStatus.INVOICED;
import static br.com.marketplace.order.domain.FulfillmentStatus.INVOICE_FAILED;
import static br.com.marketplace.order.domain.FulfillmentStatus.IN_TRANSIT;
import static br.com.marketplace.order.domain.FulfillmentStatus.REFUNDED;
import static br.com.marketplace.order.domain.FulfillmentStatus.REFUND_PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrderProgressTest {

    @Test
    void waitsForEveryInvoice() {
        assertThat(OrderProgress.derive(List.of(INVOICED, AWAITING_INVOICE))).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(OrderProgress.readyToCapture(List.of(INVOICED, AWAITING_INVOICE))).isFalse();
    }

    @Test
    void allInvoicedAllowsCapture() {
        assertThat(OrderProgress.derive(List.of(INVOICED, INVOICED))).isEqualTo(OrderStatus.INVOICED);
        assertThat(OrderProgress.readyToCapture(List.of(INVOICED, INVOICED))).isTrue();
    }

    @Test
    void rejectedInvoicePutsOrderOnHold() {
        assertThat(OrderProgress.derive(List.of(INVOICED, INVOICE_FAILED))).isEqualTo(OrderStatus.ON_HOLD);
        assertThat(OrderProgress.readyToCapture(List.of(INVOICE_FAILED))).isFalse();
    }

    @Test
    void anyMovingPartMeansInDelivery() {
        assertThat(OrderProgress.derive(List.of(INVOICED, IN_TRANSIT))).isEqualTo(OrderStatus.IN_DELIVERY);
        assertThat(OrderProgress.derive(List.of(DELIVERED, IN_TRANSIT))).isEqualTo(OrderStatus.IN_DELIVERY);
    }

    @Test
    void finalStates() {
        assertThat(OrderProgress.derive(List.of(DELIVERED, DELIVERED))).isEqualTo(OrderStatus.DELIVERED);
        assertThat(OrderProgress.derive(List.of(REFUNDED))).isEqualTo(OrderStatus.REFUNDED);
        assertThat(OrderProgress.derive(List.of(DELIVERED, REFUNDED))).isEqualTo(OrderStatus.PARTIALLY_DELIVERED);
    }

    @Test
    void lostOrReturnedPartMeansRefunding() {
        assertThat(OrderProgress.derive(List.of(DELIVERED, REFUND_PENDING))).isEqualTo(OrderStatus.REFUNDING);
    }

    @Test
    void confirmedOrderMustHaveParts() {
        assertThatThrownBy(() -> OrderProgress.derive(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
