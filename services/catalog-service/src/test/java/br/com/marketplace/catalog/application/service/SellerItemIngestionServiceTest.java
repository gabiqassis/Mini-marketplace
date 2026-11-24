package br.com.marketplace.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.catalog.application.port.out.ItemCache;
import br.com.marketplace.catalog.application.port.out.ItemRepository;
import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.SellerItemPublished;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.InvalidMessageException;
import br.com.marketplace.messaging.OutboxRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SellerItemIngestionServiceTest {

    private final ItemRepository items = mock(ItemRepository.class);
    private final ItemCache cache = mock(ItemCache.class);
    private final InboxRepository inbox = mock(InboxRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final SellerItemIngestionService service = new SellerItemIngestionService(items, cache, inbox, outbox);

    private final EventEnvelope env =
            new EventEnvelope(UUID.randomUUID(), "SellerItemPublished", Instant.now(), "batch-1", null, "ingestion", null);

    private static SellerItemPublished msg(String price, int qty) {
        return new SellerItemPublished("loja", "SKU-1", "Camiseta", new BigDecimal(price), qty, 2, "batch-1");
    }

    @Test
    void newerItemIsSavedPublishedAndCacheEvicted() {
        when(inbox.markProcessed(any(), any())).thenReturn(true);
        when(items.upsertIfNewer(any())).thenReturn(true);

        service.ingest(env, msg("10.00", 3));

        verify(outbox).publish(eq(Topics.CATALOG_ITEM_UPSERTED), eq("SKU-1"), any(), any());
        verify(cache).evict("SKU-1");
    }

    @Test
    void olderSequenceIsIgnoredSilently() {
        when(inbox.markProcessed(any(), any())).thenReturn(true);
        when(items.upsertIfNewer(any())).thenReturn(false);

        service.ingest(env, msg("10.00", 3));

        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    void duplicateMessageHasNoEffect() {
        when(inbox.markProcessed(any(), any())).thenReturn(false);

        service.ingest(env, msg("10.00", 3));

        verify(items, never()).upsertIfNewer(any());
    }

    @Test
    void invalidPayloadIsPermanentFailure() {
        when(inbox.markProcessed(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.ingest(env, msg("0", 3))).isInstanceOf(InvalidMessageException.class);
        assertThatThrownBy(() -> service.ingest(env, msg("1", -1))).isInstanceOf(InvalidMessageException.class);
    }
}
