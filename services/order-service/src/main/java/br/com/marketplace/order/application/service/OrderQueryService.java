package br.com.marketplace.order.application.service;

import br.com.marketplace.order.application.port.in.OrderQueryUseCase;
import br.com.marketplace.order.application.port.in.OrderQueryUseCase.OrderView;
import br.com.marketplace.order.domain.Fulfillment;
import br.com.marketplace.order.application.port.out.FulfillmentRepository;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.application.port.out.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryService implements OrderQueryUseCase {

    private final OrderRepository orders;
    private final FulfillmentRepository fulfillments;

    public OrderQueryService(OrderRepository orders, FulfillmentRepository fulfillments) {
        this.orders = orders;
        this.fulfillments = fulfillments;
    }

    @Override
    public Optional<OrderView> find(UUID id) {
        return orders.findById(id).map(o -> new OrderView(o, fulfillments.findByOrder(id)));
    }
}
