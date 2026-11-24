package br.com.marketplace.order.application.port.out;

import br.com.marketplace.order.domain.Fulfillment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida: partes por vendedor de cada pedido. */
public interface FulfillmentRepository {

    void insertAll(List<Fulfillment> fulfillments);

    List<Fulfillment> findByOrder(UUID orderId);

    Optional<Fulfillment> find(UUID orderId, String sellerId);

    void update(Fulfillment fulfillment);
}
