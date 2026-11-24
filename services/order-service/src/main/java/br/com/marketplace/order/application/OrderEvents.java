package br.com.marketplace.order.application;

import br.com.marketplace.contracts.Events.OrderStatusChanged;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.domain.OrderStatus;
import org.springframework.stereotype.Component;

/** Publica order.status-changed: notificacao e auditoria acompanham o pedido sem consultar o servico. */
@Component
public class OrderEvents {

    private final OutboxRepository outbox;

    public OrderEvents(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    public void statusChanged(Order order, OrderStatus status, String reason, Correlation correlation) {
        outbox.publish(Topics.ORDER_STATUS_CHANGED, order.id().toString(),
                new OrderStatusChanged(order.id(), order.buyerId(), order.buyerEmail(), status.name(), reason), correlation);
    }
}
