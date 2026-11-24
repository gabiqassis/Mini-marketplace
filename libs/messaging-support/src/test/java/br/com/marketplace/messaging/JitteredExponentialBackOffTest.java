package br.com.marketplace.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

class JitteredExponentialBackOffTest {

    @Test
    void intervalGrowsExponentiallyAndStaysBetweenHalfAndFull() {
        var min = new JitteredExponentialBackOff(500, 2.0, 10_000, 5, () -> 0.0);
        var max = new JitteredExponentialBackOff(500, 2.0, 10_000, 5, () -> 1.0);

        assertThat(min.intervalFor(0)).isEqualTo(250);
        assertThat(max.intervalFor(0)).isEqualTo(500);
        assertThat(min.intervalFor(2)).isEqualTo(1_000);
        assertThat(max.intervalFor(2)).isEqualTo(2_000);
    }

    @Test
    void intervalIsCappedAtMax() {
        var backOff = new JitteredExponentialBackOff(500, 2.0, 4_000, 10, () -> 1.0);
        assertThat(backOff.intervalFor(9)).isEqualTo(4_000);
    }

    @Test
    void stopsAfterMaxAttempts() {
        BackOffExecution exec = new JitteredExponentialBackOff(100, 2.0, 1_000, 2, () -> 0.5).start();
        assertThat(exec.nextBackOff()).isPositive();
        assertThat(exec.nextBackOff()).isPositive();
        assertThat(exec.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void jitterSpreadsClientsThatFailedTogether() {
        var a = new JitteredExponentialBackOff(1_000, 2.0, 30_000, 5, () -> 0.1);
        var b = new JitteredExponentialBackOff(1_000, 2.0, 30_000, 5, () -> 0.9);
        assertThat(a.intervalFor(1)).isNotEqualTo(b.intervalFor(1));
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new JitteredExponentialBackOff(0, 2.0, 1, 1, () -> 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
