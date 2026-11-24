package br.com.marketplace.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** Agregado Pedido. Valor total sempre calculado aqui, nunca vindo do cliente. */
public record Order(
        UUID id,
        String buyerId,
        String buyerEmail,
        ShippingAddress address,
        OrderStatus status,
        BigDecimal total,
        String cancelReason,
        boolean captureRequested,
        List<OrderItem> items) {

    public static Order place(UUID id, String buyerId, String buyerEmail, ShippingAddress address, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("pedido sem itens");
        }
        BigDecimal total = items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
        return new Order(id, buyerId, buyerEmail, address, OrderStatus.PENDING, total, null, false, List.copyOf(items));
    }

    /** Itens agrupados por vendedor (ordem estavel): cada grupo vira uma nota e um envio. */
    public Map<String, List<OrderItem>> itemsBySeller() {
        return items.stream().collect(Collectors.groupingBy(OrderItem::sellerId, TreeMap::new, Collectors.toList()));
    }

    public BigDecimal amountOf(String sellerId) {
        return items.stream().filter(i -> i.sellerId().equals(sellerId)).map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_EVEN);
    }
}
