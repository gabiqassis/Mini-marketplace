package br.com.marketplace.order.domain;

/** Erros de caso de uso traduzidos em HTTP pela camada api. */
public final class OrderExceptions {

    private OrderExceptions() {}

    public static class InvalidOrderException extends RuntimeException {
        public InvalidOrderException(String message) {
            super(message);
        }
    }

    public static class UnknownProductException extends RuntimeException {
        public UnknownProductException(String sku) {
            super("Produto desconhecido: " + sku);
        }
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException() {
            super("Idempotency-Key ja usada com outro conteudo");
        }
    }

    public static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException() {
            super("Muitas requisicoes deste comprador, tente em instantes");
        }
    }
}
