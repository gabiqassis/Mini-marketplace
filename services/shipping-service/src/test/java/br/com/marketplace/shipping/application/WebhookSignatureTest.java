package br.com.marketplace.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    private final WebhookSignature signature = new WebhookSignature("segredo-de-teste-123", Duration.ofMinutes(5));
    private final Instant now = Instant.parse("2026-11-23T12:00:00Z");
    private final String ts = Long.toString(now.getEpochSecond());
    private final String body = "{\"eventId\":\"e1\",\"status\":\"DELIVERED\"}";

    @Test
    void acceptsCorrectSignature() {
        assertThat(signature.isValid(ts, body, signature.sign(ts, body), now)).isTrue();
    }

    @Test
    void rejectsTamperedBody() {
        String sig = signature.sign(ts, body);
        assertThat(signature.isValid(ts, body.replace("DELIVERED", "LOST"), sig, now)).isFalse();
    }

    @Test
    void rejectsReplayOfOldRequest() {
        String oldTs = Long.toString(now.minusSeconds(600).getEpochSecond());
        assertThat(signature.isValid(oldTs, body, signature.sign(oldTs, body), now)).isFalse();
    }

    @Test
    void rejectsMissingOrGarbageHeaders() {
        assertThat(signature.isValid(null, body, "x", now)).isFalse();
        assertThat(signature.isValid("abc", body, "x", now)).isFalse();
        assertThat(signature.isValid(ts, body, null, now)).isFalse();
    }

    @Test
    void refusesWeakSecret() {
        assertThatThrownBy(() -> new WebhookSignature("curto", Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
    }
}
