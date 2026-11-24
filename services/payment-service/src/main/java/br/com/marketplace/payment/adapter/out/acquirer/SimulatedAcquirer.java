package br.com.marketplace.payment.adapter.out.acquirer;

import br.com.marketplace.payment.domain.GatewayResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simula o ADQUIRENTE do outro lado da rede (o "Stripe/Cielo" da demo). Guarda o que ja recebeu
 * por chave de idempotencia, como um gateway real: a mesma chave devolve a mesma resposta.
 *
 * <p>Regras para a demo:
 * <ul>
 *   <li>valor &gt; 10.000 -> recusa LIMIT_EXCEEDED</li>
 *   <li>comprador "buyer-fraud" -> recusa FRAUD_SUSPECTED</li>
 *   <li>comprador comecando com "buyer-timeout" -> APROVA, mas responde lento demais (timeout no
 *       cliente). Demonstra o estado desconhecido + reconciliacao.</li>
 *   <li>GATEWAY_FAILURE_RATE -> falha tecnica antes de processar (alimenta o circuit breaker)</li>
 * </ul>
 */
@Component
class SimulatedAcquirer {

    private static final BigDecimal LIMIT = new BigDecimal("10000");

    private final Map<String, GatewayResult> processed = new ConcurrentHashMap<>();
    private final double failureRate;
    private final long slowResponseMs;

    SimulatedAcquirer(
            @Value("${marketplace.gateway.failure-rate:0}") double failureRate,
            @Value("${marketplace.gateway.slow-response-ms:3000}") long slowResponseMs) {
        this.failureRate = failureRate;
        this.slowResponseMs = slowResponseMs;
    }

    GatewayResult authorize(String key, String buyerId, BigDecimal amount) {
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new IllegalStateException("adquirente fora do ar (simulado)");
        }
        GatewayResult result = processed.computeIfAbsent(key, k -> decide(buyerId, amount));
        networkLatency(buyerId != null && buyerId.startsWith("buyer-timeout") ? slowResponseMs : 30);
        return result;
    }

    Optional<GatewayResult> lookup(String key) {
        return Optional.ofNullable(processed.get(key));
    }

    void capture(String key) {
        processed.putIfAbsent(key, GatewayResult.approved("cap_" + UUID.randomUUID()));
    }

    void refund(String key) {
        processed.putIfAbsent(key, GatewayResult.approved("ref_" + UUID.randomUUID()));
    }

    private static GatewayResult decide(String buyerId, BigDecimal amount) {
        if (amount.compareTo(LIMIT) > 0) {
            return GatewayResult.declined("LIMIT_EXCEEDED");
        }
        if ("buyer-fraud".equals(buyerId)) {
            return GatewayResult.declined("FRAUD_SUSPECTED");
        }
        return GatewayResult.approved("auth_" + UUID.randomUUID());
    }

    private static void networkLatency(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
