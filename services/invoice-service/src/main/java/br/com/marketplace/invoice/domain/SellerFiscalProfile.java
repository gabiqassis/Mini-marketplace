package br.com.marketplace.invoice.domain;

/** Dados fiscais do emitente (o vendedor), copiados do evento seller.profile-updated. */
public record SellerFiscalProfile(String sellerId, String legalName, String cnpj) {}
