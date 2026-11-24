package br.com.marketplace.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.contracts.Topics;
import br.com.marketplace.order.application.port.in.PlaceOrderUseCase;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.order.domain.OrderExceptions.IdempotencyConflictException;
import br.com.marketplace.order.domain.OrderExceptions.InvalidOrderException;
import br.com.marketplace.order.domain.OrderExceptions.TooManyRequestsException;
import br.com.marketplace.order.domain.OrderExceptions.UnknownProductException;
import br.com.marketplace.order.domain.IdempotentOrder;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.application.port.out.OrderRepository;
import br.com.marketplace.order.domain.ProductSnapshot;
import br.com.marketplace.order.application.port.out.ProductSnapshotRepository;
import br.com.marketplace.order.application.port.out.RateLimiter;
import br.com.marketplace.order.domain.ShippingAddress;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaceOrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ProductSnapshotRepository products = mock(ProductSnapshotRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final PlaceOrderService service = new PlaceOrderService(orders, products, outbox, rateLimiter);
    private final ShippingAddress address = new ShippingAddress("13010100", "Rua A", "1", "Campinas", "SP");

    private PlaceOrderUseCase.Command cmd(String key, int qty) {
        return new PlaceOrderUseCase.Command(key, "buyer-1", "b@x.com", address, List.of(new PlaceOrderUseCase.Line("SKU-1", qty)));
    }

    @BeforeEach
    void setUp() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(orders.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(orders.insertIfAbsent(any(), any(), any())).thenReturn(true);
        when(products.findAll(any())).thenReturn(Map.of("SKU-1",
                new ProductSnapshot("SKU-1", "loja", "Camiseta", new BigDecimal("49.90"), 1)));
    }

    @Test
    void priceAndSellerComeFromCatalogSnapshotAndSagaStarts() {
        PlaceOrderUseCase.Result result = service.place(cmd("k1", 2));

        assertThat(result.created()).isTrue();
        assertThat(result.order().total()).isEqualByComparingTo("99.80");
        assertThat(result.order().items().getFirst().sellerId()).isEqualTo("loja");
        verify(outbox).publish(eq(Topics.INVENTORY_RESERVE_REQUESTED), eq(result.order().id().toString()), any(), any());
    }

    @Test
    void sameKeySameBodyReturnsOriginalOrder() {
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        Order first = service.place(cmd("k1", 2)).order();
        verify(orders).insertIfAbsent(any(), eq("k1"), hash.capture());
        when(orders.findByIdempotencyKey("k1")).thenReturn(Optional.of(new IdempotentOrder(first, hash.getValue())));

        PlaceOrderUseCase.Result replay = service.place(cmd("k1", 2));

        assertThat(replay.created()).isFalse();
        assertThat(replay.order().id()).isEqualTo(first.id());
    }

    @Test
    void sameKeyDifferentBodyIsAConflict() {
        Order first = service.place(cmd("k1", 2)).order();
        when(orders.findByIdempotencyKey("k1")).thenReturn(Optional.of(new IdempotentOrder(first, "outro-hash")));

        assertThatThrownBy(() -> service.place(cmd("k1", 3))).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void unknownProductIsRefused() {
        when(products.findAll(any())).thenReturn(Map.of());
        assertThatThrownBy(() -> service.place(cmd("k2", 1))).isInstanceOf(UnknownProductException.class);
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    void invalidInputIsRefused() {
        assertThatThrownBy(() -> service.place(cmd("", 1))).isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> service.place(cmd("k", 0))).isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> service.place(new PlaceOrderUseCase.Command("k", "b", "nao-email", address,
                List.of(new PlaceOrderUseCase.Line("SKU-1", 1))))).isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void burstFromSameBuyerIsThrottled() {
        when(rateLimiter.tryAcquire("orders:buyer-1")).thenReturn(false);
        assertThatThrownBy(() -> service.place(cmd("k3", 1))).isInstanceOf(TooManyRequestsException.class);
    }
}
