package br.com.marketplace.invoice.adapter.in.web;

import br.com.marketplace.invoice.application.port.in.InvoiceQueryUseCase;
import br.com.marketplace.invoice.domain.Invoice;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invoices")
class InvoiceController {

    private final InvoiceQueryUseCase query;

    InvoiceController(InvoiceQueryUseCase query) {
        this.query = query;
    }

    @GetMapping
    List<Invoice> byOrder(@RequestParam UUID orderId) {
        return query.byOrder(orderId);
    }
}
