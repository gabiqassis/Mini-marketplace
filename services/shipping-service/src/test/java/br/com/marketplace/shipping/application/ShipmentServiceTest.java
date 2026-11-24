package br.com.marketplace.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.shipping.domain.Shipment;
import br.com.marketplace.shipping.domain.ShipmentRepository;
import br.com.marketplace.shipping.domain.ShipmentStatus;
import br.com.marketplace.shipping.domain.TrackingCache;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipmentServiceTest {

    private final ShipmentRepository repo = mock(ShipmentRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final TrackingCache cache = mock(TrackingCache.class);
    private final ShipmentService service =
            new ShipmentService(repo, mock(InboxRepository.class), outbox, cache, Clock.systemUTC());

    private final Shipment inTransit = new Shipment(UUID.randomUUID(), UUID.randomUUID(), "loja", ShipmentStatus.IN_TRANSIT,
            "MM123456789BR", "SIMULOG", "x", 0);

    private ShipmentService.CarrierEvent event(String id, String status) {
        return new ShipmentService.CarrierEvent(id, "MM123456789BR", status, "Campinas/SP", "", Instant.now());
    }

    @Test
    void duplicateWebhookIsAcknowledgedWithoutEffect() {
        when(repo.registerCarrierEvent("e1")).thenReturn(false);

        assertThat(service.onCarrierEvent(event("e1", "DELIVERED"))).isEqualTo(ShipmentService.WebhookOutcome.DUPLICATE);
        verify(repo, never()).lockByTrackingCode(any());
    }

    @Test
    void validTransitionPublishesStatusChange() {
        when(repo.registerCarrierEvent("e2")).thenReturn(true);
        when(repo.lockByTrackingCode("MM123456789BR")).thenReturn(Optional.of(inTransit));

        assertThat(service.onCarrierEvent(event("e2", "LOST"))).isEqualTo(ShipmentService.WebhookOutcome.APPLIED);
        verify(outbox).publish(eq(Topics.SHIPMENT_STATUS_CHANGED), eq(inTransit.orderId().toString()), any(), any());
        verify(cache).evict(inTransit.orderId());
    }

    @Test
    void outOfOrderEventIsStoredButIgnored() {
        when(repo.registerCarrierEvent("e3")).thenReturn(true);
        when(repo.lockByTrackingCode(any())).thenReturn(Optional.of(inTransit));

        assertThat(service.onCarrierEvent(event("e3", "PICKED_UP"))).isEqualTo(ShipmentService.WebhookOutcome.IGNORED);
        verify(repo).addTrackingEvent(eq(inTransit.id()), any());
        verify(outbox, never()).publish(any(), any(), any(), any());
    }

    @Test
    void unknownTrackingCode() {
        when(repo.registerCarrierEvent("e4")).thenReturn(true);
        when(repo.lockByTrackingCode(any())).thenReturn(Optional.empty());

        assertThat(service.onCarrierEvent(event("e4", "DELIVERED"))).isEqualTo(ShipmentService.WebhookOutcome.UNKNOWN_TRACKING);
    }
}
