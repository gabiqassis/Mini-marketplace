package br.com.marketplace.catalog.domain;

import java.util.Optional;

/** Porta de saida: persistencia de itens. */
public interface ItemRepository {

    /**
     * Grava somente se a sequence for MAIOR que a gravada e o sku for do mesmo vendedor.
     * Torna a ingestao idempotente e imune a mensagens fora de ordem.
     *
     * @return true se gravou (novo ou atualizado)
     */
    boolean upsertIfNewer(Item item);

    Optional<Item> findBySku(String sku);
}
