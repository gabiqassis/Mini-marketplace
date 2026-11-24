package br.com.marketplace.catalog.application.service;

import br.com.marketplace.catalog.application.port.in.SellerItemIngestionUseCase;
import br.com.marketplace.catalog.domain.Item;
import br.com.marketplace.catalog.application.port.out.ItemCache;
import br.com.marketplace.catalog.application.port.out.ItemRepository;
import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CatalogItemUpserted;
import br.com.marketplace.contracts.Events.SellerItemPublished;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.InvalidMessageException;
import br.com.marketplace.messaging.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Caso de uso: vendedor publicou item/estoque (spec 001). */
@Service
public class SellerItemIngestionService implements SellerItemIngestionUseCase {

    private static final Logger log = LoggerFactory.getLogger(SellerItemIngestionService.class);
    private static final String CONSUMER = "catalog-service:seller-item-published";

    private final ItemRepository items;
    private final ItemCache cache;
    private final InboxRepository inbox;
    private final OutboxRepository outbox;

    public SellerItemIngestionService(ItemRepository items, ItemCache cache, InboxRepository inbox, OutboxRepository outbox) {
        this.items = items;
        this.cache = cache;
        this.inbox = inbox;
        this.outbox = outbox;
    }

    @Transactional
    @Override
    public void ingest(EventEnvelope envelope, SellerItemPublished msg) {
        if (!inbox.markProcessed(envelope.messageId(), CONSUMER)) {
            log.info("Mensagem {} ja processada, ignorando", envelope.messageId());
            return;
        }
        validate(msg);

        boolean changed = items.upsertIfNewer(new Item(msg.sku(), msg.sellerId(), msg.title(), msg.price(), msg.sequence()));
        if (!changed) {
            log.info("Item {} ignorado (sequence {} antiga ou sku de outro vendedor)", msg.sku(), msg.sequence());
            return;
        }
        outbox.publish(
                Topics.CATALOG_ITEM_UPSERTED,
                msg.sku(),
                new CatalogItemUpserted(msg.sellerId(), msg.sku(), msg.title(), msg.price(), msg.stockQty(), msg.sequence()),
                Correlation.from(envelope));
        evictAfterCommit(msg.sku());
    }

    private static void validate(SellerItemPublished m) {
        if (isBlank(m.sku()) || m.sku().length() > 64) {
            throw new InvalidMessageException("sku invalido");
        }
        if (isBlank(m.sellerId())) {
            throw new InvalidMessageException("sellerId obrigatorio");
        }
        if (isBlank(m.title()) || m.title().length() > 200) {
            throw new InvalidMessageException("title invalido");
        }
        if (m.price() == null || m.price().signum() <= 0) {
            throw new InvalidMessageException("price deve ser > 0");
        }
        if (m.stockQty() < 0) {
            throw new InvalidMessageException("stockQty nao pode ser negativo");
        }
        if (m.sequence() < 0) {
            throw new InvalidMessageException("sequence nao pode ser negativa");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Invalida so DEPOIS do commit: evita repopular o cache com o valor antigo. */
    private void evictAfterCommit(String sku) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.evict(sku);
                }
            });
        } else {
            cache.evict(sku);
        }
    }
}
