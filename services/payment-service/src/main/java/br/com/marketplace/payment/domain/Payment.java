package br.com.marketplace.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Agregado Pagamento. Regras de dinheiro aqui, puras e testaveis:
 * captura so do autorizado; estorno nunca maior que o capturado; antes da captura, "estorno" e VOID.
 * Invariante (tambem CHECK no banco): 0 &lt;= refunded &lt;= captured &lt;= amount.
 */
public record Payment(
        UUID id,
        UUID orderId,
        String buyerId,
        BigDecimal amount,
        PaymentStatus status,
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        String declineReason,
        String gatewayRef) {

    public enum RefundKind {
        VOID,
        REFUND
    }

    public record RefundOutcome(Payment payment, RefundKind kind, BigDecimal amount) {}

    public static Payment authorizing(UUID orderId, String buyerId, BigDecimal amount) {
        return new Payment(UUID.randomUUID(), orderId, buyerId, amount, PaymentStatus.AUTHORIZING,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }

    public Payment authorized(String ref) {
        requireStatus(PaymentStatus.AUTHORIZING);
        return with(PaymentStatus.AUTHORIZED, capturedAmount, refundedAmount, null, ref);
    }

    public Payment declined(String reason) {
        requireStatus(PaymentStatus.AUTHORIZING);
        return with(PaymentStatus.DECLINED, capturedAmount, refundedAmount, reason, gatewayRef);
    }

    public boolean canCapture() {
        return status == PaymentStatus.AUTHORIZED;
    }

    public Payment captured() {
        requireStatus(PaymentStatus.AUTHORIZED);
        return with(PaymentStatus.CAPTURED, amount, refundedAmount, null, gatewayRef);
    }

    /**
     * Estorna ate {@code requested}. Antes da captura cancela a autorizacao inteira (VOID): nada foi
     * cobrado do cliente. Depois, estorna no maximo o que ainda nao foi estornado.
     */
    public RefundOutcome refund(BigDecimal requested) {
        if (requested == null || requested.signum() <= 0) {
            throw new IllegalArgumentException("valor do estorno deve ser positivo");
        }
        if (status == PaymentStatus.AUTHORIZED) {
            return new RefundOutcome(with(PaymentStatus.VOIDED, capturedAmount, refundedAmount, null, gatewayRef),
                    RefundKind.VOID, amount);
        }
        if (status != PaymentStatus.CAPTURED && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("pagamento " + status + " nao pode ser estornado");
        }
        BigDecimal refundable = capturedAmount.subtract(refundedAmount);
        if (refundable.signum() <= 0) {
            throw new IllegalStateException("nada a estornar");
        }
        BigDecimal value = requested.min(refundable);
        BigDecimal newRefunded = refundedAmount.add(value);
        PaymentStatus next = newRefunded.compareTo(capturedAmount) == 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        return new RefundOutcome(with(next, capturedAmount, newRefunded, null, gatewayRef), RefundKind.REFUND, value);
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("esperado " + expected + ", atual " + status);
        }
    }

    private Payment with(PaymentStatus s, BigDecimal captured, BigDecimal refunded, String reason, String ref) {
        return new Payment(id, orderId, buyerId, amount, s, captured, refunded, reason, ref);
    }
}
