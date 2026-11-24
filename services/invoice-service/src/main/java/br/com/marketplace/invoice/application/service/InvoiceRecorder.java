package br.com.marketplace.invoice.application.service;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.InvoiceRejected;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.invoice.domain.Invoice;
import br.com.marketplace.invoice.application.port.out.InvoiceIssuer;
import br.com.marketplace.invoice.application.port.out.InvoiceRepository;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.OutboxRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Transacoes curtas; a chamada a SEFAZ (Lambda) fica fora delas. */
@Component
public class InvoiceRecorder {

    private final InvoiceRepository invoices;
    private final OutboxRepository outbox;

    public InvoiceRecorder(InvoiceRepository invoices, OutboxRepository outbox) {
        this.invoices = invoices;
        this.outbox = outbox;
    }

    @Transactional
    public Invoice reserve(UUID orderId, String sellerId, BigDecimal amount) {
        return invoices.reserve(orderId, sellerId, amount);
    }

    @Transactional
    public void record(EventEnvelope cause, Invoice invoice, InvoiceIssuer.Result result) {
        Invoice current = invoices.find(invoice.orderId(), invoice.sellerId()).orElseThrow();
        if (current.isFinished()) {
            return; // outra entrega do mesmo comando ja gravou
        }
        Correlation corr = Correlation.from(cause);
        if (result.authorized()) {
            Invoice done = current.authorized(result.accessKey(), result.protocol());
            invoices.update(done);
            outbox.publish(Topics.INVOICE_ISSUED, done.orderId().toString(), new InvoiceIssued(done.orderId(), done.sellerId(),
                    done.id(), done.number(), done.accessKey(), done.protocol(), done.amount()), corr);
        } else {
            invoices.update(current.rejected(result.rejectionCode(), result.rejectionReason()));
            outbox.publish(Topics.INVOICE_REJECTED, current.orderId().toString(), new InvoiceRejected(current.orderId(),
                    current.sellerId(), result.rejectionCode(), result.rejectionReason()), corr);
        }
    }

    /** Tentativas esgotadas (SEFAZ fora do ar por muito tempo): avisa o negocio, a nota fica FAILED. */
    @Transactional
    public void giveUp(EventEnvelope cause, UUID orderId, String sellerId, String reason) {
        Invoice current = invoices.find(orderId, sellerId).orElse(null);
        if (current != null && current.isFinished()) {
            return;
        }
        if (current != null) {
            invoices.update(current.failed(reason));
        }
        outbox.publish(Topics.INVOICE_REJECTED, orderId.toString(),
                new InvoiceRejected(orderId, sellerId, "TECHNICAL", Invoice.shorten("Tentativas esgotadas: " + reason)), Correlation.from(cause));
    }
}
