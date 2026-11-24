package br.com.marketplace.payment.domain;

/**
 * AUTHORIZING = pedido enviado ao gateway sem resposta (timeout): estado DESCONHECIDO, nunca
 * "recusado". A reconciliacao consulta o gateway com a mesma chave de idempotencia e decide.
 */
public enum PaymentStatus {
    AUTHORIZING,
    AUTHORIZED,
    DECLINED,
    CAPTURED,
    VOIDED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
