package br.com.marketplace.invoice.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Nota fiscal da parte de UM vendedor em um pedido. O numero e reservado ANTES de chamar a SEFAZ e
 * reaproveitado nas retentativas: a mesma nota nunca recebe dois numeros.
 */
public record Invoice(
        UUID id,
        UUID orderId,
        String sellerId,
        int series,
        long number,
        BigDecimal amount,
        InvoiceStatus status,
        String accessKey,
        String protocol,
        String rejectionCode,
        String rejectionReason) {

    public enum InvoiceStatus {
        ISSUING,
        AUTHORIZED,
        REJECTED,
        FAILED
    }

    public boolean isFinished() {
        return status != InvoiceStatus.ISSUING;
    }

    public Invoice authorized(String key, String proto) {
        return new Invoice(id, orderId, sellerId, series, number, amount, InvoiceStatus.AUTHORIZED, key, proto, null, null);
    }

    public Invoice rejected(String code, String reason) {
        return new Invoice(id, orderId, sellerId, series, number, amount, InvoiceStatus.REJECTED, null, null, code, shorten(reason));
    }

    public Invoice failed(String reason) {
        return new Invoice(id, orderId, sellerId, series, number, amount, InvoiceStatus.FAILED, null, null, "TECHNICAL", shorten(reason));
    }

    /** Motivo vem de fora (SEFAZ, SDK da AWS): limita ao tamanho da coluna para a falha nao gerar outra falha. */
    public static String shorten(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON - 3) + "...";
    }

    public static final int MAX_REASON = 300;
}
