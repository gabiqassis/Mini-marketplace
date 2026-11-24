package br.com.marketplace.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
        "PENDING, AWAITING_PAYMENT",
        "PENDING, CANCELLED",
        "AWAITING_PAYMENT, CONFIRMED",
        "AWAITING_PAYMENT, CANCELLING",
        "CANCELLING, CANCELLED",
        "CONFIRMED, INVOICED",
        "INVOICED, IN_DELIVERY",
        "IN_DELIVERY, DELIVERED",
        "IN_DELIVERY, REFUNDING",
        "REFUNDING, REFUNDED",
        "CONFIRMED, ON_HOLD",
        "ON_HOLD, INVOICED"
    })
    void allowedTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "PENDING, CONFIRMED",
        "CONFIRMED, CANCELLING",
        "CANCELLED, PENDING",
        "DELIVERED, REFUNDING",
        "REFUNDED, DELIVERED",
        "AWAITING_PAYMENT, DELIVERED",
        "CANCELLING, CONFIRMED"
    })
    void forbiddenTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void confirmedOrderCanNoLongerBeCancelledOnlyRefunded() {
        assertThat(OrderStatus.CONFIRMED.isConfirmed()).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }
}
