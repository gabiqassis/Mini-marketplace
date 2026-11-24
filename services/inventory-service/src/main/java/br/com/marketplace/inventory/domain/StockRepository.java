package br.com.marketplace.inventory.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Porta de saida: niveis de estoque. */
public interface StockRepository {

    /** Estoque do vendedor e autoridade, mas so avanca com sequence maior e nunca abaixo do ja reservado. */
    void applySellerStock(String sku, int quantity, long sequence);

    /** Trava as linhas (SELECT ... FOR UPDATE) em ordem de sku, evitando deadlock entre pedidos. */
    List<StockLevel> lockLevels(Collection<String> skus);

    void addReserved(String sku, int delta);

    /** Baixa definitiva: reduz on_hand e reserved. */
    void commit(String sku, int quantity);

    Optional<StockLevel> find(String sku);

    /** Devolucao: soma ao fisico (on_hand). */
    void restock(String sku, int quantity);
}
