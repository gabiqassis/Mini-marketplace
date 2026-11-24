package br.com.marketplace.shipping.application.service;

import br.com.marketplace.shipping.application.port.in.ShipmentUseCase;
import br.com.marketplace.shipping.application.port.in.ShipmentUseCase.CarrierEvent;
import br.com.marketplace.shipping.application.port.in.ShipmentUseCase.WebhookOutcome;
import br.com.marketplace.shipping.application.port.in.ShipmentUseCase.CarrierEvent;
import br.com.marketplace.shipping.application.port.in.ShipmentUseCase.WebhookOutcome;
import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.OrderConfirmed;
import br.com.marketplace.contracts.Events.SellerPart;
import br.com.marketplace.contracts.Events.ShipmentStatusChanged;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.shipping.domain.Shipment;
import br.com.marketplace.shipping.application.port.out.ShipmentRepository;
import br.com.marketplace.shipping.domain.ShipmentStatus;
import br.com.marketplace.shipping.application.port.out.TrackingCache;
import br.com.marketplace.shipping.domain.TrackingCode;
import br.com.marketplace.shipping.domain.TrackingEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envios:
 * 1) pedido confirmado -> reserva um envio por vendedor (aguardando nota);
 * 2) nota emitida -> gera etiqueta (uma so, mesmo com evento repetido);
 * 3) webhook da transportadora -> avanca o status respeitando a maquina de estados.
 */
@Service
public class ShipmentService implements ShipmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);
    public static final String CARRIER = "SIMULOG";

    private final ShipmentRepository shipments;
    private final InboxRepository inbox;
    private final OutboxRepository outbox;
    private final TrackingCache cache;
    private final Clock clock;

    public ShipmentService(ShipmentRepository shipments, InboxRepository inbox, OutboxRepository outbox, TrackingCache cache, Clock clock) {
        this.shipments = shipments;
        this.inbox = inbox;
        this.outbox = outbox;
        this.cache = cache;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void onOrderConfirmed(EventEnvelope env, OrderConfirmed e) {
        if (!inbox.markProcessed(env.messageId(), "shipping-service:order-confirmed")) {
            return;
        }
        String destination = e.address().zipCode() + " - " + e.address().city() + "/" + e.address().state();
        for (SellerPart part : e.sellers()) {
            shipments.insertIfAbsent(new Shipment(UUID.randomUUID(), e.orderId(), part.sellerId(),
                    ShipmentStatus.AWAITING_INVOICE, null, null, destination, 0));
        }
    }

    @Transactional
    @Override
    public void onInvoiceIssued(EventEnvelope env, InvoiceIssued e) {
        if (!inbox.markProcessed(env.messageId(), "shipping-service:invoice-issued")) {
            return;
        }
        Shipment shipment = shipments.lock(e.orderId(), e.sellerId())
                // pedido confirmado ainda nao chegou aqui: excecao = retry com backoff (nao e erro permanente)
                .orElseThrow(() -> new IllegalStateException("envio do pedido " + e.orderId() + " ainda nao reservado"));
        Shipment.Applied applied = shipment.createLabel(TrackingCode.next(() -> ThreadLocalRandom.current().nextLong()), CARRIER);
        if (applied.decision() == Shipment.Decision.IGNORED) {
            log.info("Etiqueta do pedido {} / {} ja existe ({}): nao gera outra", e.orderId(), e.sellerId(), shipment.trackingCode());
            return;
        }
        save(applied.shipment(), new TrackingEvent("CREATED", "Centro de distribuicao do vendedor",
                "Etiqueta gerada, NF-e " + e.number(), Instant.now(clock), true), Correlation.from(env));
    }

    /** Webhook da transportadora (assinatura ja verificada na borda). */
    @Transactional
    @Override
    public WebhookOutcome onCarrierEvent(CarrierEvent ev) {
        if (!shipments.registerCarrierEvent(ev.eventId())) {
            return WebhookOutcome.DUPLICATE;
        }
        ShipmentStatus next;
        try {
            next = ShipmentStatus.valueOf(ev.status());
        } catch (IllegalArgumentException e) {
            return WebhookOutcome.IGNORED;
        }
        Shipment shipment = shipments.lockByTrackingCode(ev.trackingCode()).orElse(null);
        if (shipment == null) {
            return WebhookOutcome.UNKNOWN_TRACKING;
        }
        Shipment.Applied applied = shipment.apply(next);
        TrackingEvent tracking = new TrackingEvent(ev.status(), ev.location(), ev.description(),
                ev.occurredAt() == null ? Instant.now(clock) : ev.occurredAt(), applied.decision() != Shipment.Decision.IGNORED);
        switch (applied.decision()) {
            case STATUS_CHANGED -> save(applied.shipment(), tracking, Correlation.root(shipment.orderId()));
            case CHECKPOINT -> {
                shipments.addTrackingEvent(shipment.id(), tracking);
                evictAfterCommit(shipment.orderId());
            }
            case IGNORED -> {
                shipments.addTrackingEvent(shipment.id(), tracking);
                log.warn("Evento {} da transportadora ignorado: {} -> {} nao e permitido",
                        ev.eventId(), shipment.status(), next);
            }
        }
        return switch (applied.decision()) {
            case STATUS_CHANGED -> WebhookOutcome.APPLIED;
            case CHECKPOINT -> WebhookOutcome.CHECKPOINT;
            case IGNORED -> WebhookOutcome.IGNORED;
        };
    }

    private void save(Shipment s, TrackingEvent tracking, Correlation corr) {
        shipments.update(s);
        shipments.addTrackingEvent(s.id(), tracking);
        outbox.publish(Topics.SHIPMENT_STATUS_CHANGED, s.orderId().toString(), new ShipmentStatusChanged(
                s.orderId(), s.sellerId(), s.id(), s.trackingCode(), s.status().name(), tracking.location(), tracking.occurredAt()), corr);
        evictAfterCommit(s.orderId());
    }

    /** Invalida o cache so DEPOIS do commit: senao uma leitura concorrente repopula com o dado velho. */
    private void evictAfterCommit(UUID orderId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.evict(orderId);
                }
            });
        } else {
            cache.evict(orderId);
        }
    }
}
