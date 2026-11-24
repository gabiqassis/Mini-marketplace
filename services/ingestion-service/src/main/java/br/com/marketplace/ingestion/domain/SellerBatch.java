package br.com.marketplace.ingestion.domain;

import java.math.BigDecimal;
import java.util.List;

/** Lote enviado pelo vendedor: cabecalho (quem e) + itens (o que vende e quanto tem). */
public record SellerBatch(String batchId, String sellerId, SellerProfile seller, List<Item> items) {

    public record SellerProfile(String legalName, String cnpj, String email) {}

    public record Item(String sku, String title, BigDecimal price, Integer stockQty, Long sequence) {}
}
