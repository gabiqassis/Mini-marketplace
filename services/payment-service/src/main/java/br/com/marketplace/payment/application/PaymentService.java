package br.com.marketplace.payment.application;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.AuthorizePaymentRequested;
import br.com.marketplace.contracts.Events.CapturePaymentRequested;
import br.com.marketplace.contracts.Events.RefundPaymentRequested;
import br.com.marketplace.messaging.InvalidMessageException;
import br.com.marketplace.payment.domain.GatewayResult;
import br.com.marketplace.payment.domain.Payment;
import br.com.marketplace.payment.domain.PaymentGateway;
import br.com.marketplace.payment.domain.PaymentRepository;
import br.com.marketplace.payment.domain.PaymentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de pagamento. Padrao de toda operacao externa:
 * 1) le o estado; 2) chama o gateway FORA de transacao com chave de idempotencia estavel;
 * 3) grava resultado + ledger + evento numa transacao curta (PaymentRecorder).
 * Se cair entre 2 e 3, a mensagem volta, o gateway devolve o MESMO resultado (mesma chave) e grava.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final PaymentRecorder recorder;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, PaymentRecorder recorder) {
        this.payments = payments;
        this.gateway = gateway;
        this.recorder = recorder;
    }

    static String authKey(Object orderId) {
        return "auth:" + orderId;
    }

    public void authorize(EventEnvelope env, AuthorizePaymentRequested cmd) {
        if (cmd.orderId() == null || cmd.amount() == null || cmd.amount().signum() <= 0) {
            throw new InvalidMessageException("authorize-requested invalido");
        }
        if (payments.findByOrder(cmd.orderId()).isPresent()) {
            log.info("Pedido {} ja tem pagamento: comando repetido ignorado", cmd.orderId());
            return;
        }
        GatewayResult result = gateway.authorize(authKey(cmd.orderId()), cmd.buyerId(), cmd.amount());
        if (result.outcome() == GatewayResult.Outcome.UNKNOWN) {
            log.warn("Gateway sem resposta para o pedido {}: estado DESCONHECIDO, reconciliacao vai resolver", cmd.orderId());
        }
        recorder.recordAuthorization(env, Payment.authorizing(cmd.orderId(), cmd.buyerId(), cmd.amount()), result);
    }

    public void capture(EventEnvelope env, CapturePaymentRequested cmd) {
        Payment payment = payments.findByOrder(cmd.orderId())
                .orElseThrow(() -> new IllegalStateException("pagamento do pedido " + cmd.orderId() + " ainda nao existe"));
        if (!payment.canCapture()) {
            log.info("Captura do pedido {} ignorada (status {})", cmd.orderId(), payment.status());
            return;
        }
        gateway.capture("capture:" + cmd.orderId(), payment.gatewayRef(), payment.amount());
        recorder.recordCapture(env, payment);
    }

    public void refund(EventEnvelope env, RefundPaymentRequested cmd) {
        Payment payment = payments.findByOrder(cmd.orderId())
                .orElseThrow(() -> new IllegalStateException("pagamento do pedido " + cmd.orderId() + " ainda nao existe"));
        if (payment.status() == PaymentStatus.AUTHORIZING) {
            throw new IllegalStateException("pagamento ainda em reconciliacao; estorno sera tentado de novo");
        }
        Payment.RefundOutcome outcome;
        try {
            outcome = payment.refund(cmd.amount());
        } catch (IllegalStateException e) {
            log.info("Estorno do pedido {} ignorado: {}", cmd.orderId(), e.getMessage());
            return;
        }
        gateway.refund("refund:" + cmd.orderId() + ":" + cmd.reference(), payment.gatewayRef(), outcome.amount(),
                outcome.kind() == Payment.RefundKind.VOID);
        recorder.recordRefund(env, cmd.orderId(), cmd.amount(), cmd.reference());
    }

    /**
     * Reconciliacao: pagamentos em estado desconhecido. Consulta o gateway pela chave original; se ele
     * nunca recebeu, reenvia com a MESMA chave (seguro). Apos N tentativas, recusa.
     */
    public int reconcile(Duration olderThan, int maxAttempts) {
        int resolved = 0;
        for (Payment p : payments.findAuthorizingOlderThan(Instant.now().minus(olderThan), 20)) {
            int attempt = payments.incrementAttempts(p.id());
            Optional<GatewayResult> remote = gateway.lookup(authKey(p.orderId()));
            GatewayResult result = remote.orElseGet(() -> gateway.authorize(authKey(p.orderId()), p.buyerId(), p.amount()));
            if (result.outcome() == GatewayResult.Outcome.UNKNOWN || result.outcome() == GatewayResult.Outcome.UNAVAILABLE) {
                if (attempt < maxAttempts) {
                    continue;
                }
                result = GatewayResult.declined("GATEWAY_NO_RESPONSE");
            }
            recorder.resolveUnknown(p, result);
            log.info("Pagamento do pedido {} reconciliado: {}", p.orderId(), result.outcome());
            resolved++;
        }
        return resolved;
    }

    public Optional<Payment> find(java.util.UUID orderId) {
        return payments.findByOrder(orderId);
    }
}
