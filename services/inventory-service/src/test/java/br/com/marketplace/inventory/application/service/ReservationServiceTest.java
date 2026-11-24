package br.com.marketplace.inventory.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.OrderLine;
import br.com.marketplace.contracts.Events.ReleaseStockRequested;
import br.com.marketplace.contracts.Events.ReserveStockRequested;
import br.com.marketplace.contracts.Events.RestockRequested;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.inventory.application.port.out.ReservationRepository;
import br.com.marketplace.inventory.domain.ReservationStatus;
import br.com.marketplace.inventory.domain.StockLevel;
import br.com.marketplace.inventory.application.port.out.StockRepository;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {

    private final StockRepository stock = mock(StockRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final InboxRepository inbox = mock(InboxRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final ReservationService service = new ReservationService(stock, reservations, inbox, outbox);

    private final UUID orderId = UUID.randomUUID();
    private final EventEnvelope env =
            new EventEnvelope(UUID.randomUUID(), "X", Instant.now(), orderId.toString(), null, "order-service", null);

    @BeforeEach
    void firstDelivery() {
        when(inbox.markProcessed(any(), any())).thenReturn(true);
    }

    @Test
    void reservesWhenStockIsAvailable() {
        when(reservations.status(orderId)).thenReturn(Optional.empty());
        when(stock.lockLevels(any())).thenReturn(List.of(new StockLevel("A", 5, 0)));

        service.reserve(env, new ReserveStockRequested(orderId, List.of(new OrderLine("A", 2))));

        verify(stock).addReserved("A", 2);
        verify(reservations).create(eq(orderId), eq(ReservationStatus.RESERVED), any());
        verify(outbox).publish(eq(Topics.INVENTORY_STOCK_RESERVED), eq(orderId.toString()), any(), any());
    }

    @Test
    void lastUnitRaceLoserIsRejectedWithoutTouchingStock() {
        when(reservations.status(orderId)).thenReturn(Optional.empty());
        when(stock.lockLevels(any())).thenReturn(List.of(new StockLevel("A", 1, 1)));

        service.reserve(env, new ReserveStockRequested(orderId, List.of(new OrderLine("A", 1))));

        verify(stock, never()).addReserved(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(outbox).publish(eq(Topics.INVENTORY_RESERVATION_REJECTED), any(), any(), any());
    }

    @Test
    void sameOrderTwiceRepeatsTheAnswerWithoutReservingAgain() {
        when(reservations.status(orderId)).thenReturn(Optional.of(ReservationStatus.RESERVED));

        service.reserve(env, new ReserveStockRequested(orderId, List.of(new OrderLine("A", 1))));

        verify(stock, never()).lockLevels(any());
        verify(outbox).publish(eq(Topics.INVENTORY_STOCK_RESERVED), any(), any(), any());
    }

    @Test
    void releaseBeforeReserveLeavesTombstone() {
        when(reservations.status(orderId)).thenReturn(Optional.empty());

        service.release(env, new ReleaseStockRequested(orderId, "TIMEOUT"));

        verify(reservations).create(eq(orderId), eq(ReservationStatus.RELEASED), eq(List.of()));
        verify(outbox).publish(eq(Topics.INVENTORY_STOCK_RELEASED), any(), any(), any());
    }

    @Test
    void restockHappensOncePerOrderAndSeller() {
        when(reservations.registerRestock(orderId, "loja")).thenReturn(true, false);
        RestockRequested cmd = new RestockRequested(orderId, "loja", List.of(new OrderLine("A", 2)), "RETURNED");

        service.restock(env, cmd);
        service.restock(env, cmd);

        verify(stock).restock("A", 2);
        verify(outbox).publish(eq(Topics.INVENTORY_STOCK_RESTOCKED), any(), any(), any());
    }
}
