package br.com.marketplace.messaging;

import java.util.function.DoubleSupplier;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/**
 * Backoff exponencial com JITTER ("full jitter"): cada espera e um valor aleatorio entre metade e o
 * total do intervalo exponencial. Evita o "thundering herd": varios consumidores que falharam juntos
 * nao voltam todos no mesmo milissegundo e derrubam de novo quem esta se recuperando.
 */
public class JitteredExponentialBackOff implements BackOff {

    private final long initialMs;
    private final double multiplier;
    private final long maxIntervalMs;
    private final int maxAttempts;
    private final DoubleSupplier random;

    public JitteredExponentialBackOff(long initialMs, double multiplier, long maxIntervalMs, int maxAttempts, DoubleSupplier random) {
        if (initialMs <= 0 || multiplier < 1 || maxAttempts < 0) {
            throw new IllegalArgumentException("parametros de backoff invalidos");
        }
        this.initialMs = initialMs;
        this.multiplier = multiplier;
        this.maxIntervalMs = maxIntervalMs;
        this.maxAttempts = maxAttempts;
        this.random = random;
    }

    /** Intervalo da tentativa n (0-based), ja com jitter. */
    public long intervalFor(int attempt) {
        double base = Math.min(maxIntervalMs, initialMs * Math.pow(multiplier, attempt));
        double half = base / 2.0;
        return Math.round(half + random.getAsDouble() * half);
    }

    @Override
    public BackOffExecution start() {
        return new BackOffExecution() {
            private int attempt;

            @Override
            public long nextBackOff() {
                if (attempt >= maxAttempts) {
                    return STOP;
                }
                return intervalFor(attempt++);
            }
        };
    }
}
