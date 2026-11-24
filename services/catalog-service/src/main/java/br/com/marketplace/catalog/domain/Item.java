package br.com.marketplace.catalog.domain;

import java.math.BigDecimal;

public record Item(String sku, String sellerId, String title, BigDecimal price, long sequence) {}
