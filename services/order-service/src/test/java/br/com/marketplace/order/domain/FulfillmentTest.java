package br.com.marketplace.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FulfillmentTest {

    private final Fulfillment awaiting = Fulfillment.awaitingInvoice(UUID.randomUUID(), "loja", BigDecimal.TEN);

    @Test
    void movesForward() {
        Fulfillment invoiced = awaiting.advanceTo(FulfillmentStatus.INVOICED, null).orElseThrow();
        Fulfillment moving = invoiced.advanceTo(FulfillmentStatus.IN_TRANSIT, null).orElseThrow();
        assertThat(moving.advanceTo(FulfillmentStatus.DELIVERED, null)).isPresent();
    }

    @Test
    void lateEventNeverMovesBackwards() {
        Fulfillment delivered = new Fulfillment(awaiting.orderId(), "loja", BigDecimal.TEN, FulfillmentStatus.DELIVERED, null, null, null);
        assertThat(delivered.advanceTo(FulfillmentStatus.IN_TRANSIT, null)).isEmpty();
        assertThat(delivered.advanceTo(FulfillmentStatus.REFUND_PENDING, "LOST")).isEmpty();
    }

    @Test
    void duplicateStatusIsIgnored() {
        Fulfillment moving = new Fulfillment(awaiting.orderId(), "loja", BigDecimal.TEN, FulfillmentStatus.IN_TRANSIT, null, null, null);
        assertThat(moving.advanceTo(FulfillmentStatus.IN_TRANSIT, null)).isEmpty();
    }

    @Test
    void lostPartIsRefundedAndKeepsTheProblem() {
        Fulfillment lost = new Fulfillment(awaiting.orderId(), "loja", BigDecimal.TEN, FulfillmentStatus.IN_TRANSIT, null, null, null)
                .advanceTo(FulfillmentStatus.REFUND_PENDING, "LOST").orElseThrow();
        Fulfillment refunded = lost.advanceTo(FulfillmentStatus.REFUNDED, null).orElseThrow();
        assertThat(refunded.problem()).isEqualTo("LOST");
        assertThat(refunded.status().isFinal()).isTrue();
    }

    @Test
    void failedInvoiceCanBeRetried() {
        Fulfillment failed = awaiting.advanceTo(FulfillmentStatus.INVOICE_FAILED, "INVOICE_203").orElseThrow();
        assertThat(failed.advanceTo(FulfillmentStatus.INVOICED, null)).isPresent();
    }

    @Test
    void mapsShipmentStatuses() {
        assertThat(FulfillmentStatus.fromShipment("OUT_FOR_DELIVERY")).isEqualTo(FulfillmentStatus.IN_TRANSIT);
        assertThat(FulfillmentStatus.fromShipment("DELIVERY_FAILED")).isEqualTo(FulfillmentStatus.IN_TRANSIT);
        assertThat(FulfillmentStatus.fromShipment("RETURNED")).isEqualTo(FulfillmentStatus.REFUND_PENDING);
        assertThatThrownBy(() -> FulfillmentStatus.fromShipment("TELEPORTED")).isInstanceOf(IllegalArgumentException.class);
    }
}
