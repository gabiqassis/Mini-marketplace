package br.com.marketplace.catalog.application.service;

import br.com.marketplace.catalog.application.port.in.ItemQueryUseCase;
import br.com.marketplace.catalog.domain.Item;
import br.com.marketplace.catalog.application.port.out.ItemCache;
import br.com.marketplace.catalog.application.port.out.ItemRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Leitura com cache-aside (ADR-0005): cache -> banco -> popula cache. */
@Service
public class ItemQueryService implements ItemQueryUseCase {

    private final ItemRepository items;
    private final ItemCache cache;

    public ItemQueryService(ItemRepository items, ItemCache cache) {
        this.items = items;
        this.cache = cache;
    }

    @Override
    public Optional<Item> findBySku(String sku) {
        Optional<Item> cached = cache.get(sku);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<Item> fromDb = items.findBySku(sku);
        fromDb.ifPresent(cache::put);
        return fromDb;
    }
}
