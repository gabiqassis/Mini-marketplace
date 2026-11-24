package br.com.marketplace.notification.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.InvoiceRejected;
import br.com.marketplace.contracts.Events.OrderConfirmed;
import br.com.marketplace.contracts.Events.OrderStatusChanged;
import br.com.marketplace.contracts.Events.SellerProfileUpdated;
import br.com.marketplace.contracts.Events.ShipmentStatusChanged;

/** Porta de entrada: contrato do caso de uso, implementado em NotificationService. */
public interface NotificationUseCase {

    void onSellerProfile(EventEnvelope env, SellerProfileUpdated e);

    void onOrderConfirmed(EventEnvelope env, OrderConfirmed e);

    void onOrderStatusChanged(EventEnvelope env, OrderStatusChanged e);

    void onInvoiceIssued(EventEnvelope env, InvoiceIssued e);

    void onInvoiceRejected(EventEnvelope env, InvoiceRejected e);

    void onShipmentStatusChanged(EventEnvelope env, ShipmentStatusChanged e);
}
