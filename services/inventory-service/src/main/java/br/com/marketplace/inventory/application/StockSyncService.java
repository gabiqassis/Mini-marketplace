package br.com.marketplace.inventory.application;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CatalogItemUpserted;
import br.com.marketplace.inventory.domain.StockRepository;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.InvalidMessageException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Caso de uso: quantidade informada pelo vendedor vira estoque (spec 002). */
@Service
public class StockSyncService {

    private static final String CONSUMER = "inventory-service:item-upserted";

    private final StockRepository stock;
    private final InboxRepository inbox;

    public StockSyncService(StockRepository stock, InboxRepository inbox) {
        this.stock = stock;
        this.inbox = inbox;
    }

    @Transactional
    public void onItemUpserted(EventEnvelope envelope, CatalogItemUpserted event) {
        if (!inbox.markProcessed(envelope.messageId(), CONSUMER)) {
            return;
        }
        if (event.sku() == null || event.sku().isBlank() || event.stockQty() < 0) {
            throw new InvalidMessageException("item-upserted invalido");
        }
        stock.applySellerStock(event.sku(), event.stockQty(), event.sequence());
    }
}
