package br.com.marketplace.payment.application.port.out;

import br.com.marketplace.payment.domain.GatewayResult;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Porta de saida para o adquirente (camada anticorrupcao). Toda operacao recebe uma chave de
 * idempotencia: repetir a chamada com a MESMA chave nunca cobra duas vezes.
 */
public interface PaymentGateway {

    GatewayResult authorize(String idempotencyKey, String buyerId, BigDecimal amount);

    /** Consulta pela chave original (reconciliacao). Vazio = o gateway nunca recebeu a requisicao. */
    Optional<GatewayResult> lookup(String idempotencyKey);

    /** @throws GatewayException falha tecnica (tente de novo com a mesma chave) */
    void capture(String idempotencyKey, String gatewayRef, BigDecimal amount);

    /** @throws GatewayException falha tecnica (tente de novo com a mesma chave) */
    void refund(String idempotencyKey, String gatewayRef, BigDecimal amount, boolean voidAuthorization);

    class GatewayException extends RuntimeException {
        public GatewayException(String message) {
            super(message);
        }
    }
}
