package br.com.marketplace.payment.infra;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.AuthorizePaymentRequested;
import br.com.marketplace.contracts.Events.CapturePaymentRequested;
import br.com.marketplace.contracts.Events.RefundPaymentRequested;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Envelopes;
import br.com.marketplace.messaging.LogContext;
import br.com.marketplace.payment.application.PaymentService;
import java.util.function.BiConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PaymentListeners {

    private final Envelopes envelopes;
    private final PaymentService payments;

    PaymentListeners(Envelopes envelopes, PaymentService payments) {
        this.envelopes = envelopes;
        this.payments = payments;
    }

    private <T> void dispatch(String json, Class<T> type, BiConsumer<EventEnvelope, T> handler) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            handler.accept(env, envelopes.payload(env, type));
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_AUTHORIZE_REQUESTED)
    void onAuthorize(String json) {
        dispatch(json, AuthorizePaymentRequested.class, payments::authorize);
    }

    @KafkaListener(topics = Topics.PAYMENT_CAPTURE_REQUESTED)
    void onCapture(String json) {
        dispatch(json, CapturePaymentRequested.class, payments::capture);
    }

    @KafkaListener(topics = Topics.PAYMENT_REFUND_REQUESTED)
    void onRefund(String json) {
        dispatch(json, RefundPaymentRequested.class, payments::refund);
    }
}
