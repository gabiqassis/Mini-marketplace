package br.com.marketplace.invoice.application;

import br.com.marketplace.invoice.domain.Invoice;
import br.com.marketplace.invoice.domain.InvoiceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InvoiceQueryService {

    private final InvoiceRepository invoices;

    public InvoiceQueryService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    public List<Invoice> byOrder(UUID orderId) {
        return invoices.findByOrder(orderId);
    }
}
