package br.com.marketplace.inventory.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CatalogItemUpserted;

/** Porta de entrada: contrato do caso de uso, implementado em StockSyncService. */
public interface StockSyncUseCase {

    void onItemUpserted(EventEnvelope envelope, CatalogItemUpserted event);
}
