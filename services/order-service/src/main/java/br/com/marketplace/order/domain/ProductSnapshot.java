package br.com.marketplace.order.domain;

import java.math.BigDecimal;

/** Copia local do catalogo (alimentada por evento): o pedido nunca chama o catalogo por HTTP. */
public record ProductSnapshot(String sku, String sellerId, String title, BigDecimal price, long sequence) {}
