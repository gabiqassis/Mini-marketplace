package br.com.marketplace.inventory.adapter.in.messaging;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CatalogItemUpserted;
import br.com.marketplace.contracts.Events.CommitStockRequested;
import br.com.marketplace.contracts.Events.ReleaseStockRequested;
import br.com.marketplace.contracts.Events.ReserveStockRequested;
import br.com.marketplace.contracts.Events.RestockRequested;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.inventory.application.port.in.ReservationUseCase;
import br.com.marketplace.inventory.application.port.in.StockSyncUseCase;
import br.com.marketplace.messaging.Envelopes;
import br.com.marketplace.messaging.LogContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Adaptadores de entrada do inventario: so leem o envelope e delegam para o caso de uso. */
@Component
class InventoryListeners {

    private final Envelopes envelopes;
    private final StockSyncUseCase sync;
    private final ReservationUseCase reservations;

    InventoryListeners(Envelopes envelopes, StockSyncUseCase sync, ReservationUseCase reservations) {
        this.envelopes = envelopes;
        this.sync = sync;
        this.reservations = reservations;
    }

    @KafkaListener(topics = Topics.CATALOG_ITEM_UPSERTED)
    void onItemUpserted(String json) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            sync.onItemUpserted(env, envelopes.payload(env, CatalogItemUpserted.class));
        }
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVE_REQUESTED)
    void onReserve(String json) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            reservations.reserve(env, envelopes.payload(env, ReserveStockRequested.class));
        }
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMIT_REQUESTED)
    void onCommit(String json) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            reservations.commit(env, envelopes.payload(env, CommitStockRequested.class));
        }
    }

    @KafkaListener(topics = Topics.INVENTORY_RELEASE_REQUESTED)
    void onRelease(String json) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            reservations.release(env, envelopes.payload(env, ReleaseStockRequested.class));
        }
    }

    @KafkaListener(topics = Topics.INVENTORY_RESTOCK_REQUESTED)
    void onRestock(String json) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            reservations.restock(env, envelopes.payload(env, RestockRequested.class));
        }
    }
}
