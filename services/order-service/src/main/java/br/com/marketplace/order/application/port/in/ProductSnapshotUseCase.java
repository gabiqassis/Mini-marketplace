package br.com.marketplace.order.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CatalogItemUpserted;

/** Porta de entrada: contrato do caso de uso, implementado em ProductSnapshotService. */
public interface ProductSnapshotUseCase {

    void onItemUpserted(EventEnvelope envelope, CatalogItemUpserted e);
}
