package br.com.marketplace.catalog.domain;

import java.util.Optional;

/** Porta de saida: cache de leitura. Cache e otimizacao: falha dele nunca pode quebrar a leitura. */
public interface ItemCache {

    Optional<Item> get(String sku);

    void put(Item item);

    void evict(String sku);
}
