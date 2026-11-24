package br.com.marketplace.order.adapter.in.web;

import br.com.marketplace.messaging.LogContext;
import br.com.marketplace.order.domain.OrderExceptions.InvalidOrderException;
import br.com.marketplace.order.application.port.in.OrderQueryUseCase;
import br.com.marketplace.order.application.port.in.PlaceOrderUseCase;
import br.com.marketplace.order.domain.ShippingAddress;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
class OrderController {

    private final PlaceOrderUseCase placeOrder;
    private final OrderQueryUseCase query;

    OrderController(PlaceOrderUseCase placeOrder, OrderQueryUseCase query) {
        this.placeOrder = placeOrder;
        this.query = query;
    }

    /** 201 = pedido criado; 200 = mesma Idempotency-Key repetida (devolve o pedido original). */
    @PostMapping
    ResponseEntity<OrderResponse> place(
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderUseCase.Result result = placeOrder.place(new PlaceOrderUseCase.Command(
                idempotencyKey,
                request.buyerId(),
                request.buyerEmail(),
                toAddress(request.shippingAddress()),
                request.items().stream().map(i -> new PlaceOrderUseCase.Line(i.sku(), i.quantity())).toList()));
        OrderResponse body = OrderResponse.from(result.order());
        try (var ignored = LogContext.of(body.id().toString())) {
            if (result.created()) {
                return ResponseEntity.created(URI.create("/orders/" + body.id())).body(body);
            }
            return ResponseEntity.ok(body);
        }
    }

    @GetMapping("/{id}")
    ResponseEntity<OrderResponse> get(@PathVariable UUID id) {
        return query.find(id).map(v -> ResponseEntity.ok(OrderResponse.from(v))).orElse(ResponseEntity.notFound().build());
    }

    private static ShippingAddress toAddress(PlaceOrderRequest.Address a) {
        try {
            return new ShippingAddress(a.zipCode(), a.street(), a.number(), a.city(), a.state());
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException(e.getMessage());
        }
    }
}
