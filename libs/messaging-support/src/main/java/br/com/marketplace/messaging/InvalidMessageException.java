package br.com.marketplace.messaging;

/**
 * Falha PERMANENTE: mensagem malformada ou regra que nunca vai passar. Nao adianta tentar de novo:
 * vai direto para a DLT (Kafka) ou parking (RabbitMQ).
 */
public class InvalidMessageException extends RuntimeException {

    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
