package br.com.marketplace.order.adapter.in.scheduler;

import br.com.marketplace.order.application.port.in.SagaUseCase;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Thread agendada: dispara a compensacao de pedidos presos na SAGA (resposta perdida, servico fora). */
@Component
class SagaTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutScheduler.class);

    private final SagaUseCase saga;
    private final Duration pendingTimeout;
    private final Duration paymentTimeout;

    SagaTimeoutScheduler(
            SagaUseCase saga,
            @Value("${marketplace.saga.pending-timeout:PT2M}") Duration pendingTimeout,
            @Value("${marketplace.saga.payment-timeout:PT5M}") Duration paymentTimeout) {
        this.saga = saga;
        this.pendingTimeout = pendingTimeout;
        this.paymentTimeout = paymentTimeout;
    }

    @Scheduled(fixedDelayString = "${marketplace.saga.sweep-interval-ms:15000}")
    void sweep() {
        try {
            int n = saga.cancelStale(pendingTimeout, paymentTimeout);
            if (n > 0) {
                log.warn("{} pedido(s) em compensacao por timeout", n);
            }
        } catch (RuntimeException e) {
            log.warn("Varredura de timeout falhou: {}", e.getMessage());
        }
    }
}
