package br.com.marketplace.invoice.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.SellerProfileUpdated;

/** Porta de entrada: contrato do caso de uso, implementado em SellerProfileService. */
public interface SellerProfileUseCase {

    void onProfileUpdated(EventEnvelope env, SellerProfileUpdated e);
}
