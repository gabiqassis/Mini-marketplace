package br.com.marketplace.order.domain;

import java.math.BigDecimal;

/** Linha do pedido com preco e vendedor congelados no momento da compra. */
public record OrderItem(String sku, String sellerId, String title, int quantity, BigDecimal unitPrice) {

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
