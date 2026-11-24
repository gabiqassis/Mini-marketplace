package br.com.marketplace.messaging;

import br.com.marketplace.contracts.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Le e escreve envelopes. Erro de formato vira InvalidMessageException (nao adianta tentar de novo). */
public class Envelopes {

    private final ObjectMapper mapper;
    private final String producer;
    private final Clock clock;

    public Envelopes(ObjectMapper mapper, String producer, Clock clock) {
        // Leitor TOLERANTE por contrato (nao por configuracao externa): campo novo nao quebra consumidor antigo.
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.producer = producer;
        this.clock = clock;
    }

    public EventEnvelope wrap(Object payload, Correlation correlation) {
        return new EventEnvelope(
                UUID.randomUUID(),
                payload.getClass().getSimpleName(),
                Instant.now(clock),
                correlation.correlationId(),
                correlation.causationId(),
                producer,
                mapper.valueToTree(payload));
    }

    public String write(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar envelope", e);
        }
    }

    public EventEnvelope read(String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidMessageException("Mensagem vazia");
        }
        try {
            EventEnvelope env = mapper.readValue(json, EventEnvelope.class);
            if (env.messageId() == null || env.payload() == null || env.payload().isNull()) {
                throw new InvalidMessageException("Envelope sem messageId ou payload");
            }
            return env;
        } catch (JsonProcessingException e) {
            throw new InvalidMessageException("Envelope ilegivel: " + e.getOriginalMessage(), e);
        }
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        try {
            return mapper.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidMessageException("Payload invalido para " + type.getSimpleName(), e);
        }
    }
}
