package br.com.marketplace.inventory.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CommitStockRequested;
import br.com.marketplace.contracts.Events.ReleaseStockRequested;
import br.com.marketplace.contracts.Events.ReserveStockRequested;
import br.com.marketplace.contracts.Events.RestockRequested;

/** Porta de entrada: contrato do caso de uso, implementado em ReservationService. */
public interface ReservationUseCase {

    void reserve(EventEnvelope envelope, ReserveStockRequested cmd);

    void commit(EventEnvelope envelope, CommitStockRequested cmd);

    void release(EventEnvelope envelope, ReleaseStockRequested cmd);

    void restock(EventEnvelope envelope, RestockRequested cmd);
}
