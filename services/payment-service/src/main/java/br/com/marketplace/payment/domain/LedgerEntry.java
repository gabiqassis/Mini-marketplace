package br.com.marketplace.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Lancamento imutavel do ledger (append-only) de um pagamento. */
public record LedgerEntry(String type, BigDecimal amount, String reference, Instant at) {}
