package br.com.marketplace.order.application.port.in;

import br.com.marketplace.order.domain.Fulfillment;
import br.com.marketplace.order.domain.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de entrada: contrato do caso de uso, implementado em OrderQueryService. */
public interface OrderQueryUseCase {

    record OrderView(Order order, List<Fulfillment> fulfillments) {}

    Optional<OrderView> find(UUID id);
}
