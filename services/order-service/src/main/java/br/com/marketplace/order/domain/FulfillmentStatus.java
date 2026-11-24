package br.com.marketplace.order.domain;

/**
 * Estado da parte de UM vendedor dentro do pedido (marketplace: um pedido, varios vendedores,
 * uma nota e um envio por vendedor). rank impede andar para tras com evento atrasado.
 */
public enum FulfillmentStatus {
    AWAITING_INVOICE(0),
    INVOICE_FAILED(1),
    INVOICED(1),
    IN_TRANSIT(2),
    DELIVERED(3),
    REFUND_PENDING(3),
    REFUNDED(4);

    private final int rank;

    FulfillmentStatus(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean isFinal() {
        return this == DELIVERED || this == REFUNDED;
    }

    /**
     * Traduz o status do envio (shipping-service) para o estado da parte do vendedor.
     * Extravio e devolucao viram REFUND_PENDING: o comprador sera reembolsado.
     */
    public static FulfillmentStatus fromShipment(String shipmentStatus) {
        return switch (shipmentStatus) {
            case "CREATED" -> INVOICED;
            case "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERY_FAILED" -> IN_TRANSIT;
            case "DELIVERED" -> DELIVERED;
            case "LOST", "RETURNED" -> REFUND_PENDING;
            default -> throw new IllegalArgumentException("status de envio desconhecido: " + shipmentStatus);
        };
    }
}
