package br.com.marketplace.messaging;

import br.com.marketplace.contracts.EventEnvelope;
import java.util.Objects;
import java.util.UUID;

/**
 * Fio de rastreabilidade carregado de mensagem em mensagem.
 *
 * @param correlationId identificador do fluxo inteiro (orderId na compra, batchId no vendedor)
 * @param causationId messageId da mensagem que causou a proxima (nulo na origem)
 */
public record Correlation(String correlationId, UUID causationId) {

    public Correlation {
        Objects.requireNonNull(correlationId, "correlationId");
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId vazio");
        }
    }

    /** Inicio de um fluxo (ex.: pedido criado pela API). */
    public static Correlation root(Object correlationId) {
        return new Correlation(String.valueOf(correlationId), null);
    }

    /** Continuacao: a nova mensagem herda o fluxo e aponta para quem a causou. */
    public static Correlation from(EventEnvelope cause) {
        String id = cause.correlationId() != null ? cause.correlationId() : cause.messageId().toString();
        return new Correlation(id, cause.messageId());
    }
}
