package br.com.marketplace.shipping.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida do envio (inspirado nos status/substatus de envio de marketplaces):
 *
 * <pre>
 * AWAITING_INVOICE -> CREATED (etiqueta) -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
 *                                                                           |
 *                                                                           v
 *                                              OUT_FOR_DELIVERY <- DELIVERY_FAILED -> RETURNED
 * Qualquer estado em movimento -> LOST (extravio)
 * </pre>
 */
public enum ShipmentStatus {
    AWAITING_INVOICE,
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERY_FAILED,
    DELIVERED,
    LOST,
    RETURNED;

    private static final Set<ShipmentStatus> MOVING = EnumSet.of(CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERY_FAILED);

    public boolean isTerminal() {
        return this == DELIVERED || this == LOST || this == RETURNED;
    }

    public boolean canTransitionTo(ShipmentStatus next) {
        if (next == LOST) {
            return MOVING.contains(this);
        }
        return switch (this) {
            case AWAITING_INVOICE -> next == CREATED;
            case CREATED -> next == PICKED_UP;
            case PICKED_UP -> next == IN_TRANSIT;
            case IN_TRANSIT -> next == OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> next == DELIVERED || next == DELIVERY_FAILED;
            case DELIVERY_FAILED -> next == OUT_FOR_DELIVERY || next == RETURNED;
            case DELIVERED, LOST, RETURNED -> false;
        };
    }

    /** Transportadora manda o mesmo status com novo local (checkpoint): vale como rastreio, nao como mudanca. */
    public boolean isCheckpoint(ShipmentStatus next) {
        return this == next && (this == IN_TRANSIT || this == OUT_FOR_DELIVERY);
    }
}
