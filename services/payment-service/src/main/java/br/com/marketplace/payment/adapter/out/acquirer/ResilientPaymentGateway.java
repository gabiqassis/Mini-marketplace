package br.com.marketplace.payment.adapter.out.acquirer;

import br.com.marketplace.payment.domain.GatewayResult;
import br.com.marketplace.payment.application.port.out.PaymentGateway;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador do gateway com as tres protecoes classicas:
 * <ol>
 *   <li>TIMEOUT: cada chamada roda numa virtual thread e espera no maximo {@code timeout}.
 *       Estourou = UNKNOWN (pode ter sido aprovado!), nunca "recusado".</li>
 *   <li>BULKHEAD: Semaphore limita chamadas simultaneas; gateway lento nao consome todas as threads.</li>
 *   <li>CIRCUIT BREAKER: muitas falhas seguidas abrem o circuito; enquanto aberto, nem chama
 *       (UNAVAILABLE, seguro recusar porque a requisicao nao saiu) e o gateway tem tempo de voltar.</li>
 * </ol>
 */
@Component
class ResilientPaymentGateway implements PaymentGateway, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ResilientPaymentGateway.class);

    private final SimulatedAcquirer acquirer;
    private final Duration timeout;
    private final Semaphore bulkhead;
    private final CircuitBreaker breaker;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    ResilientPaymentGateway(
            SimulatedAcquirer acquirer,
            @Value("${marketplace.gateway.timeout:PT1.5S}") Duration timeout,
            @Value("${marketplace.gateway.max-concurrent-calls:20}") int maxConcurrent) {
        this.acquirer = acquirer;
        this.timeout = timeout;
        this.bulkhead = new Semaphore(maxConcurrent);
        this.breaker = CircuitBreaker.of("payment-gateway", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
        breaker.getEventPublisher().onStateTransition(e -> log.warn("Circuit breaker do gateway: {}", e.getStateTransition()));
    }

    @Override
    public GatewayResult authorize(String idempotencyKey, String buyerId, BigDecimal amount) {
        try {
            return call(() -> acquirer.authorize(idempotencyKey, buyerId, amount));
        } catch (CallNotPermittedException | BulkheadFullException e) {
            log.warn("Gateway protegido ({}): requisicao NAO enviada", e.getClass().getSimpleName());
            return GatewayResult.unavailable();
        } catch (TimeoutException e) {
            return GatewayResult.unknown();
        } catch (RuntimeException e) {
            log.warn("Falha tecnica no gateway: {}", e.getMessage());
            return GatewayResult.unavailable();
        }
    }

    @Override
    public Optional<GatewayResult> lookup(String idempotencyKey) {
        return acquirer.lookup(idempotencyKey);
    }

    @Override
    public void capture(String idempotencyKey, String gatewayRef, BigDecimal amount) {
        runOrFail(() -> {
            acquirer.capture(idempotencyKey);
            return null;
        });
    }

    @Override
    public void refund(String idempotencyKey, String gatewayRef, BigDecimal amount, boolean voidAuthorization) {
        runOrFail(() -> {
            acquirer.refund(idempotencyKey);
            return null;
        });
    }

    private void runOrFail(Supplier<Void> op) {
        try {
            call(op);
        } catch (TimeoutException | RuntimeException e) {
            throw new GatewayException("falha no gateway, tentar de novo com a mesma chave: " + e.getMessage());
        }
    }

    private <T> T call(Supplier<T> op) throws TimeoutException {
        if (!bulkhead.tryAcquire()) {
            throw new BulkheadFullException();
        }
        try {
            return breaker.executeCheckedSupplier(() -> {
                Future<T> future = executor.submit(op::get);
                try {
                    return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    throw e;
                } catch (ExecutionException e) {
                    throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
                }
            });
        } catch (TimeoutException | RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        } finally {
            bulkhead.release();
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    static class BulkheadFullException extends RuntimeException {
        BulkheadFullException() {
            super("muitas chamadas simultaneas ao gateway");
        }
    }
}
