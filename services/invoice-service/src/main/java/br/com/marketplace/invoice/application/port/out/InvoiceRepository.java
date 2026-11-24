package br.com.marketplace.invoice.application.port.out;

import br.com.marketplace.invoice.domain.Invoice;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida: notas e numeracao. */
public interface InvoiceRepository {

    Optional<Invoice> find(UUID orderId, String sellerId);

    /**
     * Reserva numero (sequencia por vendedor) e cria a nota ISSUING, se ainda nao existir para
     * (pedido, vendedor). Idempotente: retentativa devolve a mesma nota e o mesmo numero.
     */
    Invoice reserve(UUID orderId, String sellerId, BigDecimal amount);

    void update(Invoice invoice);

    List<Invoice> findByOrder(UUID orderId);
}
