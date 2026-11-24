package br.com.marketplace.order.domain;

import java.util.Collection;

/** Regra pura: status do pedido confirmado a partir das partes de cada vendedor. */
public final class OrderProgress {

    private OrderProgress() {}

    public static OrderStatus derive(Collection<FulfillmentStatus> parts) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("pedido confirmado sem partes");
        }
        if (parts.contains(FulfillmentStatus.INVOICE_FAILED)) {
            return OrderStatus.ON_HOLD;
        }
        if (parts.contains(FulfillmentStatus.AWAITING_INVOICE)) {
            return OrderStatus.CONFIRMED;
        }
        if (parts.contains(FulfillmentStatus.REFUND_PENDING)) {
            return OrderStatus.REFUNDING;
        }
        if (all(parts, FulfillmentStatus.DELIVERED)) {
            return OrderStatus.DELIVERED;
        }
        if (all(parts, FulfillmentStatus.REFUNDED)) {
            return OrderStatus.REFUNDED;
        }
        boolean onlyFinal = parts.stream().allMatch(FulfillmentStatus::isFinal);
        if (onlyFinal) {
            return OrderStatus.PARTIALLY_DELIVERED;
        }
        boolean moving = parts.stream().anyMatch(p -> p.rank() >= FulfillmentStatus.IN_TRANSIT.rank());
        return moving ? OrderStatus.IN_DELIVERY : OrderStatus.INVOICED;
    }

    /** Captura o pagamento so quando TODAS as notas foram autorizadas (regra fiscal do marketplace). */
    public static boolean readyToCapture(Collection<FulfillmentStatus> parts) {
        return !parts.isEmpty() && parts.stream().noneMatch(p ->
                p == FulfillmentStatus.AWAITING_INVOICE || p == FulfillmentStatus.INVOICE_FAILED);
    }

    private static boolean all(Collection<FulfillmentStatus> parts, FulfillmentStatus s) {
        return parts.stream().allMatch(p -> p == s);
    }
}
