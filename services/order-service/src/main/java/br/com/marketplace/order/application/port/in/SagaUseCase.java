package br.com.marketplace.order.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.PaymentAuthorized;
import br.com.marketplace.contracts.Events.PaymentDeclined;
import br.com.marketplace.contracts.Events.StockReleased;
import br.com.marketplace.contracts.Events.StockReservationRejected;
import br.com.marketplace.contracts.Events.StockReserved;
import java.time.Duration;

/** Porta de entrada: contrato do caso de uso, implementado em SagaCoordinator. */
public interface SagaUseCase {

    void onStockReserved(EventEnvelope env, StockReserved e);

    void onReservationRejected(EventEnvelope env, StockReservationRejected e);

    void onPaymentAuthorized(EventEnvelope env, PaymentAuthorized e);

    void onPaymentDeclined(EventEnvelope env, PaymentDeclined e);

    void onStockReleased(EventEnvelope env, StockReleased e);

    int cancelStale(Duration pendingTimeout, Duration paymentTimeout);
}
