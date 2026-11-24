package br.com.marketplace.catalog.adapter.in.messaging;

import br.com.marketplace.catalog.application.port.in.SellerItemIngestionUseCase;
import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.SellerItemPublished;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Envelopes;
import br.com.marketplace.messaging.LogContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Adaptador de entrada: le o envelope e delega. Falhou? O error handler faz retry e DLT. */
@Component
class SellerItemListener {

    private final Envelopes envelopes;
    private final SellerItemIngestionUseCase service;

    SellerItemListener(Envelopes envelopes, SellerItemIngestionUseCase service) {
        this.envelopes = envelopes;
        this.service = service;
    }

    @KafkaListener(topics = Topics.SELLER_ITEM_PUBLISHED)
    void onMessage(String json) {
        EventEnvelope envelope = envelopes.read(json);
        try (var ignored = LogContext.of(envelope)) {
            service.ingest(envelope, envelopes.payload(envelope, SellerItemPublished.class));
        }
    }
}
