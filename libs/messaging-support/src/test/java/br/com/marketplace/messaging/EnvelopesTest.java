package br.com.marketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.marketplace.contracts.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnvelopesTest {

    record Sample(String sku, int qty) {}

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final Envelopes envelopes =
            new Envelopes(mapper, "test-service", Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void wrapCarriesCorrelationCausationAndProducer() {
        UUID cause = UUID.randomUUID();
        EventEnvelope env = envelopes.wrap(new Sample("SKU-1", 2), new Correlation("order-1", cause));

        assertThat(env.messageId()).isNotNull();
        assertThat(env.type()).isEqualTo("Sample");
        assertThat(env.correlationId()).isEqualTo("order-1");
        assertThat(env.causationId()).isEqualTo(cause);
        assertThat(env.producer()).isEqualTo("test-service");
        assertThat(env.occurredAt()).isEqualTo(Instant.parse("2026-11-23T12:00:00Z"));
    }

    @Test
    void roundTripPreservesPayload() {
        EventEnvelope env = envelopes.wrap(new Sample("SKU-1", 2), Correlation.root("order-1"));
        EventEnvelope read = envelopes.read(envelopes.write(env));

        assertThat(read.messageId()).isEqualTo(env.messageId());
        assertThat(envelopes.payload(read, Sample.class)).isEqualTo(new Sample("SKU-1", 2));
    }

    @Test
    void unknownFieldsAreTolerated() {
        String json = """
                {"messageId":"%s","type":"Sample","payload":{"sku":"A","qty":1,"novoCampo":true},"extra":1}
                """.formatted(UUID.randomUUID());
        assertThat(envelopes.payload(envelopes.read(json), Sample.class).sku()).isEqualTo("A");
    }

    @Test
    void garbageIsAPermanentFailure() {
        assertThatThrownBy(() -> envelopes.read("{nao e json")).isInstanceOf(InvalidMessageException.class);
        assertThatThrownBy(() -> envelopes.read("")).isInstanceOf(InvalidMessageException.class);
        assertThatThrownBy(() -> envelopes.read("{\"type\":\"x\"}")).isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void continuationInheritsFlowAndPointsToCause() {
        EventEnvelope cause = envelopes.wrap(new Sample("A", 1), Correlation.root("order-9"));
        Correlation next = Correlation.from(cause);

        assertThat(next.correlationId()).isEqualTo("order-9");
        assertThat(next.causationId()).isEqualTo(cause.messageId());
    }

    @Test
    void blankCorrelationIsRejected() {
        assertThatThrownBy(() -> Correlation.root(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
