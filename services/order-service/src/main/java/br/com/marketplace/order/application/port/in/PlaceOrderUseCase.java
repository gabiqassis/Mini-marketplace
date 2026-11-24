package br.com.marketplace.order.application.port.in;

import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.domain.ShippingAddress;
import java.util.List;

/** Porta de entrada: comprador faz um pedido (inicia a SAGA). Implementado em PlaceOrderService. */
public interface PlaceOrderUseCase {

    record Line(String sku, int quantity) {}

    record Command(String idempotencyKey, String buyerId, String buyerEmail, ShippingAddress address, List<Line> lines) {}

    record Result(Order order, boolean created) {}

    Result place(Command cmd);
}
