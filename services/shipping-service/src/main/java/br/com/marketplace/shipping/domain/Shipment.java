package br.com.marketplace.shipping.domain;

import java.util.UUID;

/** Envio da parte de UM vendedor. UNIQUE(order_id, seller_id): nunca duas etiquetas para a mesma parte. */
public record Shipment(
        UUID id,
        UUID orderId,
        String sellerId,
        ShipmentStatus status,
        String trackingCode,
        String carrier,
        String destination,
        int deliveryAttempts) {

    public enum Decision {
        STATUS_CHANGED,
        CHECKPOINT,
        IGNORED
    }

    public record Applied(Shipment shipment, Decision decision) {}

    /** Gera a etiqueta: so a partir de AWAITING_INVOICE. Segunda nota/evento repetido nao gera outra. */
    public Applied createLabel(String code, String carrierName) {
        if (status != ShipmentStatus.AWAITING_INVOICE) {
            return new Applied(this, Decision.IGNORED);
        }
        return new Applied(new Shipment(id, orderId, sellerId, ShipmentStatus.CREATED, code, carrierName, destination, 0),
                Decision.STATUS_CHANGED);
    }

    /** Aplica um evento da transportadora respeitando a maquina de estados. */
    public Applied apply(ShipmentStatus next) {
        if (status.isCheckpoint(next)) {
            return new Applied(this, Decision.CHECKPOINT);
        }
        if (!status.canTransitionTo(next)) {
            return new Applied(this, Decision.IGNORED);
        }
        int attempts = next == ShipmentStatus.DELIVERY_FAILED ? deliveryAttempts + 1 : deliveryAttempts;
        return new Applied(new Shipment(id, orderId, sellerId, next, trackingCode, carrier, destination, attempts),
                Decision.STATUS_CHANGED);
    }
}
