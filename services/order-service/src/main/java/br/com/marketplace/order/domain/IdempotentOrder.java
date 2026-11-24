package br.com.marketplace.order.domain;

/** Pedido ja criado com uma Idempotency-Key, junto do hash da requisicao original. */
public record IdempotentOrder(Order order, String requestHash) {}
