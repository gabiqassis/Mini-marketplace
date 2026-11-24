package br.com.marketplace.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.marketplace.messaging.InvalidMessageException;
import br.com.marketplace.messaging.rabbit.RabbitRetryPolicy.Decision;
import org.junit.jupiter.api.Test;

class RabbitRetryPolicyTest {

    private final RabbitRetryPolicy policy = new RabbitRetryPolicy(3);

    @Test
    void transientFailureIsRetriedWhileAttemptsRemain() {
        assertThat(policy.decide(0, new IllegalStateException("timeout"))).isEqualTo(Decision.RETRY);
        assertThat(policy.decide(1, new IllegalStateException("timeout"))).isEqualTo(Decision.RETRY);
    }

    @Test
    void givesUpOnLastAttempt() {
        assertThat(policy.decide(2, new IllegalStateException("timeout"))).isEqualTo(Decision.GIVE_UP);
    }

    @Test
    void permanentFailureIsNeverRetried() {
        assertThat(policy.decide(0, new InvalidMessageException("cnpj invalido"))).isEqualTo(Decision.GIVE_UP);
    }
}
