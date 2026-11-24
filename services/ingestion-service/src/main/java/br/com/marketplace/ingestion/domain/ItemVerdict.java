package br.com.marketplace.ingestion.domain;

/** Resultado da validacao de UM item: aceito, ou rejeitado com motivo legivel para o vendedor. */
public record ItemVerdict(SellerBatch.Item item, boolean accepted, String reason) {

    public static ItemVerdict accept(SellerBatch.Item item) {
        return new ItemVerdict(item, true, null);
    }

    public static ItemVerdict reject(SellerBatch.Item item, String reason) {
        return new ItemVerdict(item, false, reason);
    }
}
