package br.com.marketplace.payment.application.service;

import br.com.marketplace.payment.domain.LedgerEntry;
import br.com.marketplace.payment.application.port.in.PaymentQueryUseCase;
import br.com.marketplace.payment.application.port.in.PaymentQueryUseCase.PaymentView;
import br.com.marketplace.payment.domain.Payment;
import br.com.marketplace.payment.application.port.out.PaymentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentQueryService implements PaymentQueryUseCase {

    private final PaymentRepository payments;

    public PaymentQueryService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public Optional<PaymentView> byOrder(UUID orderId) {
        return payments.findByOrder(orderId).map(p -> new PaymentView(p, payments.ledger(p.id())));
    }
}
