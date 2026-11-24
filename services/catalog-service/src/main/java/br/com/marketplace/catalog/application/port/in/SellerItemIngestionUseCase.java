package br.com.marketplace.catalog.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.SellerItemPublished;

/** Porta de entrada: contrato do caso de uso, implementado em SellerItemIngestionService. */
public interface SellerItemIngestionUseCase {

    void ingest(EventEnvelope envelope, SellerItemPublished msg);
}
