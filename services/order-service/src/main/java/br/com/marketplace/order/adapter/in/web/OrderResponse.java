package br.com.marketplace.order.adapter.in.web;

import br.com.marketplace.order.application.port.in.OrderQueryUseCase.OrderView;
import br.com.marketplace.order.domain.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

record OrderResponse(
        UUID id,
        String buyerId,
        String status,
        BigDecimal total,
        String reason,
        List<Item> items,
        List<Part> sellers) {

    record Item(String sku, String sellerId, String title, int quantity, BigDecimal unitPrice) {}

    record Part(String sellerId, BigDecimal amount, String status, String problem, String invoiceKey, String trackingCode) {}

    static OrderResponse from(Order o) {
        return from(new OrderView(o, List.of()));
    }

    static OrderResponse from(OrderView v) {
        Order o = v.order();
        return new OrderResponse(
                o.id(),
                o.buyerId(),
                o.status().name(),
                o.total(),
                o.cancelReason(),
                o.items().stream().map(i -> new Item(i.sku(), i.sellerId(), i.title(), i.quantity(), i.unitPrice())).toList(),
                v.fulfillments().stream()
                        .map(f -> new Part(f.sellerId(), f.amount(), f.status().name(), f.problem(), f.invoiceKey(), f.trackingCode()))
                        .toList());
    }
}
