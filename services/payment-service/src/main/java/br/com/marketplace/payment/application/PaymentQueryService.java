package br.com.marketplace.payment.application;

import br.com.marketplace.payment.domain.Payment;
import br.com.marketplace.payment.domain.PaymentRepository;
import br.com.marketplace.payment.domain.PaymentRepository.LedgerEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentQueryService {

    public record PaymentView(Payment payment, List<LedgerEntry> ledger) {}

    private final PaymentRepository payments;

    public PaymentQueryService(PaymentRepository payments) {
        this.payments = payments;
    }

    public Optional<PaymentView> byOrder(UUID orderId) {
        return payments.findByOrder(orderId).map(p -> new PaymentView(p, payments.ledger(p.id())));
    }
}
