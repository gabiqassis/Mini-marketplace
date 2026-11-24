package br.com.marketplace.inventory.domain;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Regra pura da reserva "tudo ou nada": ou todos os itens do pedido cabem no disponivel, ou nada e
 * reservado. Evita o oversell parcial (metade do pedido reservada e a outra metade sem estoque).
 */
public final class ReservationPolicy {

    private ReservationPolicy() {}

    /** Soma linhas repetidas do mesmo sku e ordena por sku (ordem de lock estavel = sem deadlock). */
    public static Map<String, Integer> merge(Iterable<ReservedLine> lines) {
        Map<String, Integer> merged = new TreeMap<>();
        for (ReservedLine line : lines) {
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("quantidade deve ser > 0");
            }
            merged.merge(line.sku(), line.quantity(), Integer::sum);
        }
        return merged;
    }

    /** @return motivo da recusa, ou vazio se todo o pedido cabe no estoque disponivel. */
    public static Optional<String> rejectionReason(Map<String, Integer> wanted, Map<String, StockLevel> levels) {
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            StockLevel level = levels.get(e.getKey());
            if (level == null) {
                return Optional.of("UNKNOWN_SKU:" + e.getKey());
            }
            if (level.available() < e.getValue()) {
                return Optional.of("INSUFFICIENT_STOCK:" + e.getKey());
            }
        }
        return Optional.empty();
    }
}
