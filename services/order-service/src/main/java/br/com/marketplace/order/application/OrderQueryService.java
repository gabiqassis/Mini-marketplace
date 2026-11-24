package br.com.marketplace.order.application;

import br.com.marketplace.order.domain.Fulfillment;
import br.com.marketplace.order.domain.FulfillmentRepository;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.domain.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryService {

    public record OrderView(Order order, List<Fulfillment> fulfillments) {}

    private final OrderRepository orders;
    private final FulfillmentRepository fulfillments;

    public OrderQueryService(OrderRepository orders, FulfillmentRepository fulfillments) {
        this.orders = orders;
        this.fulfillments = fulfillments;
    }

    public Optional<OrderView> find(UUID id) {
        return orders.findById(id).map(o -> new OrderView(o, fulfillments.findByOrder(id)));
    }
}
