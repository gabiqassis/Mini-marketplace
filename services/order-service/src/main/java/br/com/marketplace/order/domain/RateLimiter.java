package br.com.marketplace.order.domain;

/** Porta de saida: limite de requisicoes por comprador. Se o limitador falhar, deixa passar (fail-open). */
public interface RateLimiter {

    boolean tryAcquire(String key);
}
