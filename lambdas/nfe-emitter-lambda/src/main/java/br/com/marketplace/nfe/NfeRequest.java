package br.com.marketplace.nfe;

import java.math.BigDecimal;
import java.time.Instant;

/** Dados minimos para "autorizar" uma NF-e. O numero vem do invoice-service (dono da numeracao). */
public record NfeRequest(
        String orderId, String sellerId, String sellerCnpj, int series, long number, BigDecimal amount, Instant issuedAt) {}
