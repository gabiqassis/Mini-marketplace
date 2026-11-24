package br.com.marketplace.order.domain;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Parte de um vendedor no pedido: valor, nota e envio dela. */
public record Fulfillment(
        UUID orderId,
        String sellerId,
        BigDecimal amount,
        FulfillmentStatus status,
        String problem,
        String invoiceKey,
        String trackingCode) {

    public static Fulfillment awaitingInvoice(UUID orderId, String sellerId, BigDecimal amount) {
        return new Fulfillment(orderId, sellerId, amount, FulfillmentStatus.AWAITING_INVOICE, null, null, null);
    }

    /**
     * Avanca para o proximo estado SOMENTE se for para frente. Evento atrasado ou repetido (ex.: "em
     * transito" chegando depois de "entregue") e ignorado: devolve vazio.
     */
    public Optional<Fulfillment> advanceTo(FulfillmentStatus next, String problem) {
        if (status.isFinal() || next.rank() <= status.rank()) {
            boolean refundAfterProblem = status == FulfillmentStatus.REFUND_PENDING && next == FulfillmentStatus.REFUNDED;
            boolean retryInvoice = status == FulfillmentStatus.INVOICE_FAILED && next == FulfillmentStatus.INVOICED;
            if (!refundAfterProblem && !retryInvoice) {
                return Optional.empty();
            }
        }
        return Optional.of(new Fulfillment(orderId, sellerId, amount, next, problem != null ? problem : this.problem,
                invoiceKey, trackingCode));
    }

    public Fulfillment withInvoiceKey(String key) {
        return new Fulfillment(orderId, sellerId, amount, status, problem, key, trackingCode);
    }

    public Fulfillment withTrackingCode(String code) {
        return new Fulfillment(orderId, sellerId, amount, status, problem, invoiceKey, code);
    }
}
