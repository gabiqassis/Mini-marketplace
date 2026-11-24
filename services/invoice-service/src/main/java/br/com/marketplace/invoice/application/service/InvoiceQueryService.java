package br.com.marketplace.invoice.application.service;

import br.com.marketplace.invoice.application.port.in.InvoiceQueryUseCase;
import br.com.marketplace.invoice.domain.Invoice;
import br.com.marketplace.invoice.application.port.out.InvoiceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InvoiceQueryService implements InvoiceQueryUseCase {

    private final InvoiceRepository invoices;

    public InvoiceQueryService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Override
    public List<Invoice> byOrder(UUID orderId) {
        return invoices.findByOrder(orderId);
    }
}
