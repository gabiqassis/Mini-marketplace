package br.com.marketplace.invoice.application;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.SellerProfileUpdated;
import br.com.marketplace.invoice.domain.SellerFiscalProfile;
import br.com.marketplace.invoice.domain.SellerProfileRepository;
import br.com.marketplace.messaging.InboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerProfileService {

    private final SellerProfileRepository sellers;
    private final InboxRepository inbox;

    public SellerProfileService(SellerProfileRepository sellers, InboxRepository inbox) {
        this.sellers = sellers;
        this.inbox = inbox;
    }

    @Transactional
    public void onProfileUpdated(EventEnvelope env, SellerProfileUpdated e) {
        if (inbox.markProcessed(env.messageId(), "invoice-service:seller-profile")) {
            sellers.upsert(new SellerFiscalProfile(e.sellerId(), e.legalName(), e.cnpj()));
        }
    }
}
