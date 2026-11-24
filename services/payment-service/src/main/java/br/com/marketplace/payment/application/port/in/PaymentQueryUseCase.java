package br.com.marketplace.payment.application.port.in;

import br.com.marketplace.payment.domain.LedgerEntry;
import br.com.marketplace.payment.domain.LedgerEntry;
import br.com.marketplace.payment.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de entrada: contrato do caso de uso, implementado em PaymentQueryService. */
public interface PaymentQueryUseCase {

    record PaymentView(Payment payment, List<LedgerEntry> ledger) {}

    Optional<PaymentView> byOrder(UUID orderId);
}
