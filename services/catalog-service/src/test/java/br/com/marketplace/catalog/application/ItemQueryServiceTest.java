package br.com.marketplace.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.catalog.domain.Item;
import br.com.marketplace.catalog.domain.ItemCache;
import br.com.marketplace.catalog.domain.ItemRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItemQueryServiceTest {

    private final ItemRepository repo = mock(ItemRepository.class);
    private final ItemCache cache = mock(ItemCache.class);
    private final ItemQueryService service = new ItemQueryService(repo, cache);
    private final Item item = new Item("SKU-1", "loja", "Camiseta", BigDecimal.TEN, 1);

    @Test
    void cacheHitDoesNotTouchDatabase() {
        when(cache.get("SKU-1")).thenReturn(Optional.of(item));

        assertThat(service.findBySku("SKU-1")).contains(item);
        verify(repo, never()).findBySku("SKU-1");
    }

    @Test
    void cacheMissReadsDatabaseAndPopulatesCache() {
        when(cache.get("SKU-1")).thenReturn(Optional.empty());
        when(repo.findBySku("SKU-1")).thenReturn(Optional.of(item));

        assertThat(service.findBySku("SKU-1")).contains(item);
        verify(cache).put(item);
    }

    @Test
    void unknownSkuIsNotCached() {
        when(cache.get("X")).thenReturn(Optional.empty());
        when(repo.findBySku("X")).thenReturn(Optional.empty());

        assertThat(service.findBySku("X")).isEmpty();
        verify(cache, never()).put(org.mockito.ArgumentMatchers.any());
    }
}
