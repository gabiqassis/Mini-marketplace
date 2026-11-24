package br.com.marketplace.order.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.InvoiceRejected;
import br.com.marketplace.contracts.Events.PaymentRefunded;
import br.com.marketplace.contracts.Events.ShipmentStatusChanged;

/** Porta de entrada: contrato do caso de uso, implementado em FulfillmentCoordinator. */
public interface FulfillmentUseCase {

    void onInvoiceIssued(EventEnvelope env, InvoiceIssued e);

    void onInvoiceRejected(EventEnvelope env, InvoiceRejected e);

    void onShipmentStatusChanged(EventEnvelope env, ShipmentStatusChanged e);

    void onPaymentRefunded(EventEnvelope env, PaymentRefunded e);
}
