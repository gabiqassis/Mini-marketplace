package br.com.marketplace.shipping.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShipmentTest {

    private Shipment at(ShipmentStatus status) {
        return new Shipment(UUID.randomUUID(), UUID.randomUUID(), "loja", status, "MM123456789BR", "SIMULOG", "x", 0);
    }

    @ParameterizedTest
    @CsvSource({
        "AWAITING_INVOICE, CREATED",
        "CREATED, PICKED_UP",
        "PICKED_UP, IN_TRANSIT",
        "IN_TRANSIT, OUT_FOR_DELIVERY",
        "OUT_FOR_DELIVERY, DELIVERED",
        "OUT_FOR_DELIVERY, DELIVERY_FAILED",
        "DELIVERY_FAILED, OUT_FOR_DELIVERY",
        "DELIVERY_FAILED, RETURNED",
        "IN_TRANSIT, LOST"
    })
    void validTransitions(ShipmentStatus from, ShipmentStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "DELIVERED, IN_TRANSIT",
        "DELIVERED, LOST",
        "CREATED, DELIVERED",
        "AWAITING_INVOICE, PICKED_UP",
        "LOST, DELIVERED",
        "AWAITING_INVOICE, LOST"
    })
    void outOfOrderOrImpossibleTransitionsAreRejected(ShipmentStatus from, ShipmentStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void labelIsCreatedOnlyOnce() {
        Shipment.Applied first = at(ShipmentStatus.AWAITING_INVOICE).createLabel("MM000000001BR", "SIMULOG");
        assertThat(first.decision()).isEqualTo(Shipment.Decision.STATUS_CHANGED);
        assertThat(first.shipment().trackingCode()).isEqualTo("MM000000001BR");

        Shipment.Applied second = first.shipment().createLabel("MM000000002BR", "SIMULOG");
        assertThat(second.decision()).isEqualTo(Shipment.Decision.IGNORED);
        assertThat(second.shipment().trackingCode()).isEqualTo("MM000000001BR");
    }

    @Test
    void sameStatusWithNewLocationIsACheckpoint() {
        assertThat(at(ShipmentStatus.IN_TRANSIT).apply(ShipmentStatus.IN_TRANSIT).decision()).isEqualTo(Shipment.Decision.CHECKPOINT);
    }

    @Test
    void failedDeliveryAttemptsAreCounted() {
        Shipment failed = at(ShipmentStatus.OUT_FOR_DELIVERY).apply(ShipmentStatus.DELIVERY_FAILED).shipment();
        Shipment failedAgain = failed.apply(ShipmentStatus.OUT_FOR_DELIVERY).shipment().apply(ShipmentStatus.DELIVERY_FAILED).shipment();
        assertThat(failedAgain.deliveryAttempts()).isEqualTo(2);
    }

    @Test
    void trackingCodeFollowsCorreiosFormat() {
        assertThat(TrackingCode.isValid(TrackingCode.next(() -> -42L))).isTrue();
        assertThat(TrackingCode.isValid("123")).isFalse();
    }
}
