package br.com.marketplace.ingestion.domain;

import java.util.List;

/** O que o vendedor recebe de volta: o que entrou, o que foi recusado e por que. */
public record BatchReport(String sellerId, String batchId, String source, int accepted, int rejected, List<ItemResult> items) {

    public record ItemResult(String sku, String status, String reason) {}
}
