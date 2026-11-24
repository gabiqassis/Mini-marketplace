package br.com.marketplace.payment.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.AuthorizePaymentRequested;
import br.com.marketplace.contracts.Events.CapturePaymentRequested;
import br.com.marketplace.contracts.Events.RefundPaymentRequested;
import br.com.marketplace.payment.domain.Payment;
import java.time.Duration;
import java.util.Optional;

/** Porta de entrada: contrato do caso de uso, implementado em PaymentService. */
public interface PaymentUseCase {

    void authorize(EventEnvelope env, AuthorizePaymentRequested cmd);

    void capture(EventEnvelope env, CapturePaymentRequested cmd);

    void refund(EventEnvelope env, RefundPaymentRequested cmd);

    int reconcile(Duration olderThan, int maxAttempts);

    Optional<Payment> find(java.util.UUID orderId);
}
