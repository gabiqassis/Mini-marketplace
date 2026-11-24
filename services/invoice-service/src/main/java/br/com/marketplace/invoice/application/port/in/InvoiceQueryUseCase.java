package br.com.marketplace.invoice.application.port.in;

import br.com.marketplace.invoice.domain.Invoice;
import java.util.List;
import java.util.UUID;

/** Porta de entrada: contrato do caso de uso, implementado em InvoiceQueryService. */
public interface InvoiceQueryUseCase {

    List<Invoice> byOrder(UUID orderId);
}
