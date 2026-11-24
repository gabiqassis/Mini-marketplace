package br.com.marketplace.payment.application.port.out;

import br.com.marketplace.payment.domain.LedgerEntry;
import br.com.marketplace.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida: pagamentos e ledger (livro-razao append-only). */
public interface PaymentRepository {

    /** UNIQUE(order_id): um pagamento por pedido. @return false se ja existia. */
    boolean insertIfAbsent(Payment payment);

    Optional<Payment> findByOrder(UUID orderId);

    Optional<Payment> lockByOrder(UUID orderId);

    void update(Payment payment);

    /** UNIQUE(payment_id, type, reference). @return false se o lancamento ja existia (idempotencia). */
    boolean appendLedger(UUID paymentId, String type, BigDecimal amount, String reference);

    List<LedgerEntry> ledger(UUID paymentId);

    /** Pagamentos em estado desconhecido ha mais de {@code olderThan}, para reconciliar. */
    List<Payment> findAuthorizingOlderThan(Instant olderThan, int limit);

    /** Conta a tentativa de reconciliacao. @return total de tentativas ate agora. */
    int incrementAttempts(UUID paymentId);

}
