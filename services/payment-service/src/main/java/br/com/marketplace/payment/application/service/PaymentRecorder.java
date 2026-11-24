package br.com.marketplace.payment.application.service;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.PaymentAuthorized;
import br.com.marketplace.contracts.Events.PaymentCaptured;
import br.com.marketplace.contracts.Events.PaymentDeclined;
import br.com.marketplace.contracts.Events.PaymentRefunded;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.payment.domain.GatewayResult;
import br.com.marketplace.payment.domain.Payment;
import br.com.marketplace.payment.application.port.out.PaymentRepository;
import br.com.marketplace.payment.domain.PaymentStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tudo que GRAVA fica aqui, em transacoes curtas, separado da chamada ao gateway (que nunca roda
 * dentro de transacao: segurar conexao do banco esperando a rede derruba o pool sob carga).
 */
@Component
public class PaymentRecorder {

    private final PaymentRepository payments;
    private final InboxRepository inbox;
    private final OutboxRepository outbox;

    public PaymentRecorder(PaymentRepository payments, InboxRepository inbox, OutboxRepository outbox) {
        this.payments = payments;
        this.inbox = inbox;
        this.outbox = outbox;
    }

    /** Registra o resultado da autorizacao. UNKNOWN fica AUTHORIZING ate a reconciliacao decidir. */
    @Transactional
    public void recordAuthorization(EventEnvelope env, Payment authorizing, GatewayResult result) {
        if (!inbox.markProcessed(env.messageId(), "payment-service:authorize")) {
            return;
        }
        if (!payments.insertIfAbsent(authorizing)) {
            return; // outro comando do mesmo pedido ja criou o pagamento
        }
        apply(authorizing, result, Correlation.from(env));
    }

    /** Reconciliacao: aplica a resposta tardia ao pagamento que estava em estado desconhecido. */
    @Transactional
    public void resolveUnknown(Payment current, GatewayResult result) {
        Payment locked = payments.lockByOrder(current.orderId()).orElseThrow();
        if (locked.status() != PaymentStatus.AUTHORIZING) {
            return;
        }
        apply(locked, result, Correlation.root(locked.orderId()));
    }

    private void apply(Payment payment, GatewayResult result, Correlation corr) {
        switch (result.outcome()) {
            case APPROVED -> {
                Payment authorized = payment.authorized(result.gatewayRef());
                payments.update(authorized);
                payments.appendLedger(authorized.id(), "AUTHORIZATION", authorized.amount(), "order");
                outbox.publish(Topics.PAYMENT_AUTHORIZED, payment.orderId().toString(),
                        new PaymentAuthorized(payment.orderId(), payment.id(), payment.amount()), corr);
            }
            case DECLINED, UNAVAILABLE -> {
                payments.update(payment.declined(result.declineCode()));
                outbox.publish(Topics.PAYMENT_DECLINED, payment.orderId().toString(),
                        new PaymentDeclined(payment.orderId(), result.declineCode()), corr);
            }
            case UNKNOWN -> {
                // continua AUTHORIZING: nao publica nada; a reconciliacao resolve
            }
        }
    }

    @Transactional
    public boolean recordCapture(EventEnvelope env, Payment authorized) {
        if (!inbox.markProcessed(env.messageId(), "payment-service:capture")) {
            return false;
        }
        Payment locked = payments.lockByOrder(authorized.orderId()).orElseThrow();
        if (!locked.canCapture()) {
            return false;
        }
        Payment captured = locked.captured();
        payments.update(captured);
        payments.appendLedger(captured.id(), "CAPTURE", captured.capturedAmount(), "order");
        outbox.publish(Topics.PAYMENT_CAPTURED, captured.orderId().toString(),
                new PaymentCaptured(captured.orderId(), captured.id(), captured.capturedAmount()), Correlation.from(env));
        return true;
    }

    /**
     * Recalcula o estorno sob lock (dois estornos do mesmo pedido em paralelo nao estornam a mais).
     * O ledger UNIQUE(payment, tipo, referencia) garante um estorno por referencia.
     */
    @Transactional
    public void recordRefund(EventEnvelope env, java.util.UUID orderId, java.math.BigDecimal requested, String reference) {
        if (!inbox.markProcessed(env.messageId(), "payment-service:refund")) {
            return;
        }
        Payment locked = payments.lockByOrder(orderId).orElseThrow();
        Payment.RefundOutcome outcome;
        try {
            outcome = locked.refund(requested);
        } catch (IllegalStateException e) {
            return;
        }
        Payment p = outcome.payment();
        if (!payments.appendLedger(p.id(), outcome.kind().name(), outcome.amount(), reference)) {
            return; // este estorno (mesma referencia) ja foi feito
        }
        payments.update(p);
        outbox.publish(Topics.PAYMENT_REFUNDED, p.orderId().toString(),
                new PaymentRefunded(p.orderId(), reference, outcome.amount(), outcome.kind().name()), Correlation.from(env));
    }
}
