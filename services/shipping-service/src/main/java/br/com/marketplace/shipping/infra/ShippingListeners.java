package br.com.marketplace.shipping.infra;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.OrderConfirmed;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Envelopes;
import br.com.marketplace.messaging.LogContext;
import br.com.marketplace.shipping.application.ShipmentService;
import java.util.function.BiConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class ShippingListeners {

    private final Envelopes envelopes;
    private final ShipmentService shipments;

    ShippingListeners(Envelopes envelopes, ShipmentService shipments) {
        this.envelopes = envelopes;
        this.shipments = shipments;
    }

    private <T> void dispatch(String json, Class<T> type, BiConsumer<EventEnvelope, T> handler) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            handler.accept(env, envelopes.payload(env, type));
        }
    }

    @KafkaListener(topics = Topics.ORDER_CONFIRMED)
    void onOrderConfirmed(String json) {
        dispatch(json, OrderConfirmed.class, shipments::onOrderConfirmed);
    }

    @KafkaListener(topics = Topics.INVOICE_ISSUED)
    void onInvoiceIssued(String json) {
        dispatch(json, InvoiceIssued.class, shipments::onInvoiceIssued);
    }
}
