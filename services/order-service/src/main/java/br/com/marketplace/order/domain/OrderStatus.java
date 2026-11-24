package br.com.marketplace.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Maquina de estados do pedido.
 *
 * <pre>
 * SAGA de compra (transicoes guardadas):
 *   PENDING -> AWAITING_PAYMENT -> CONFIRMED
 *      |             |
 *      v             v
 *   CANCELLED    CANCELLING -> CANCELLED          (compensacao: libera estoque)
 *
 * Pos-venda (derivado das partes de cada vendedor, ver OrderProgress):
 *   CONFIRMED -> INVOICED -> IN_DELIVERY -> DELIVERED | PARTIALLY_DELIVERED
 *   qualquer parte extraviada/devolvida -> REFUNDING -> REFUNDED
 *   nota rejeitada -> ON_HOLD (vendedor precisa corrigir)
 * </pre>
 */
public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    CONFIRMED,
    INVOICED,
    IN_DELIVERY,
    DELIVERED,
    PARTIALLY_DELIVERED,
    REFUNDING,
    REFUNDED,
    ON_HOLD,
    CANCELLING,
    CANCELLED;

    private static final Set<OrderStatus> AFTER_CONFIRMATION =
            EnumSet.of(CONFIRMED, INVOICED, IN_DELIVERY, DELIVERED, PARTIALLY_DELIVERED, REFUNDING, REFUNDED, ON_HOLD);

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == AWAITING_PAYMENT || target == CANCELLING || target == CANCELLED;
            case AWAITING_PAYMENT -> target == CONFIRMED || target == CANCELLING;
            case CANCELLING -> target == CANCELLED;
            case CANCELLED, DELIVERED, REFUNDED -> false;
            default -> isConfirmed() && target.isConfirmed() && target != this;
        };
    }

    /** Pagamento autorizado e estoque baixado: daqui em diante so existe pos-venda. */
    public boolean isConfirmed() {
        return AFTER_CONFIRMATION.contains(this);
    }

    public boolean isCancelled() {
        return this == CANCELLING || this == CANCELLED;
    }
}
