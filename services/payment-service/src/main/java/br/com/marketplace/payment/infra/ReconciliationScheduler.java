package br.com.marketplace.payment.infra;

import br.com.marketplace.payment.application.PaymentService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Thread agendada que resolve pagamentos em estado desconhecido (timeout do gateway). */
@Component
class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final PaymentService payments;
    private final Duration olderThan;
    private final int maxAttempts;

    ReconciliationScheduler(
            PaymentService payments,
            @Value("${marketplace.reconciliation.older-than:PT5S}") Duration olderThan,
            @Value("${marketplace.reconciliation.max-attempts:5}") int maxAttempts) {
        this.payments = payments;
        this.olderThan = olderThan;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${marketplace.reconciliation.interval-ms:5000}")
    void reconcile() {
        try {
            int n = payments.reconcile(olderThan, maxAttempts);
            if (n > 0) {
                log.info("{} pagamento(s) reconciliado(s)", n);
            }
        } catch (RuntimeException e) {
            log.warn("Reconciliacao falhou, tentara de novo: {}", e.getMessage());
        }
    }
}
