package br.com.marketplace.order.application;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CatalogItemUpserted;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.order.domain.ProductSnapshot;
import br.com.marketplace.order.domain.ProductSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductSnapshotService {

    private final ProductSnapshotRepository snapshots;
    private final InboxRepository inbox;

    public ProductSnapshotService(ProductSnapshotRepository snapshots, InboxRepository inbox) {
        this.snapshots = snapshots;
        this.inbox = inbox;
    }

    @Transactional
    public void onItemUpserted(EventEnvelope envelope, CatalogItemUpserted e) {
        if (!inbox.markProcessed(envelope.messageId(), "order-service:item-upserted")) {
            return;
        }
        snapshots.upsertIfNewer(new ProductSnapshot(e.sku(), e.sellerId(), e.title(), e.price(), e.sequence()));
    }
}
