package br.com.marketplace.shipping.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.OrderConfirmed;
import java.time.Instant;

/** Porta de entrada: ciclo de vida do envio (pedido confirmado, nota emitida, evento da transportadora). */
public interface ShipmentUseCase {

    enum WebhookOutcome {
        APPLIED,
        CHECKPOINT,
        IGNORED,
        DUPLICATE,
        UNKNOWN_TRACKING
    }

    record CarrierEvent(String eventId, String trackingCode, String status, String location, String description, Instant occurredAt) {}

    void onOrderConfirmed(EventEnvelope env, OrderConfirmed e);

    void onInvoiceIssued(EventEnvelope env, InvoiceIssued e);

    WebhookOutcome onCarrierEvent(CarrierEvent ev);
}
