package br.com.marketplace.invoice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Porta de saida: quem autoriza a NF-e. Adaptadores: AWS Lambda (padrao) ou in-process (ambiente
 * sem Lambda). O caso de uso nao sabe qual esta ativo.
 */
public interface InvoiceIssuer {

    record Request(UUID orderId, String sellerId, String sellerCnpj, int series, long number, BigDecimal amount, Instant issuedAt) {}

    record Result(boolean authorized, String accessKey, String protocol, String rejectionCode, String rejectionReason) {}

    /**
     * @return AUTORIZADA ou REJEITADA (rejeicao fiscal: nao adianta repetir)
     * @throws TechnicalFailure SEFAZ/Lambda indisponivel: tentar de novo
     */
    Result issue(Request request);

    class TechnicalFailure extends RuntimeException {
        public TechnicalFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
