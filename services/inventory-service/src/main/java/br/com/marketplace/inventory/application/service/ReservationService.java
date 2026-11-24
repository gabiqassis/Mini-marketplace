package br.com.marketplace.inventory.application.service;

import br.com.marketplace.inventory.application.port.in.ReservationUseCase;
import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CommitStockRequested;
import br.com.marketplace.contracts.Events.OrderLine;
import br.com.marketplace.contracts.Events.ReleaseStockRequested;
import br.com.marketplace.contracts.Events.ReserveStockRequested;
import br.com.marketplace.contracts.Events.RestockRequested;
import br.com.marketplace.contracts.Events.StockReleased;
import br.com.marketplace.contracts.Events.StockReservationRejected;
import br.com.marketplace.contracts.Events.StockReserved;
import br.com.marketplace.contracts.Events.StockRestocked;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.inventory.domain.ReservationPolicy;
import br.com.marketplace.inventory.application.port.out.ReservationRepository;
import br.com.marketplace.inventory.domain.ReservationStatus;
import br.com.marketplace.inventory.domain.ReservedLine;
import br.com.marketplace.inventory.domain.StockLevel;
import br.com.marketplace.inventory.application.port.out.StockRepository;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.InboxRepository;
import br.com.marketplace.messaging.InvalidMessageException;
import br.com.marketplace.messaging.OutboxRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Participante da SAGA. Tudo e idempotente em DOIS niveis:
 * inbox (mesma mensagem) e reservations.order_id (mesmo pedido, mensagens diferentes).
 * Oversell e impossivel: lock pessimista ordenado + CHECK (reserved &lt;= on_hand) no banco.
 */
@Service
public class ReservationService implements ReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final StockRepository stock;
    private final ReservationRepository reservations;
    private final InboxRepository inbox;
    private final OutboxRepository outbox;

    public ReservationService(StockRepository stock, ReservationRepository reservations, InboxRepository inbox, OutboxRepository outbox) {
        this.stock = stock;
        this.reservations = reservations;
        this.inbox = inbox;
        this.outbox = outbox;
    }

    @Transactional
    @Override
    public void reserve(EventEnvelope envelope, ReserveStockRequested cmd) {
        if (!inbox.markProcessed(envelope.messageId(), "inventory-service:reserve")) {
            return;
        }
        if (cmd.orderId() == null || cmd.lines() == null || cmd.lines().isEmpty()) {
            throw new InvalidMessageException("reserve-requested sem orderId ou linhas");
        }
        UUID orderId = cmd.orderId();
        Correlation corr = Correlation.from(envelope);

        Optional<ReservationStatus> existing = reservations.status(orderId);
        if (existing.isPresent()) {
            // Mesmo pedido de novo (outra mensagem): repete a MESMA resposta, sem reservar duas vezes.
            replyForExisting(orderId, existing.get(), corr);
            return;
        }

        Map<String, Integer> wanted;
        try {
            wanted = ReservationPolicy.merge(toLines(cmd.lines()));
        } catch (IllegalArgumentException e) {
            throw new InvalidMessageException(e.getMessage());
        }
        Map<String, StockLevel> levels = stock.lockLevels(wanted.keySet()).stream()
                .collect(Collectors.toMap(StockLevel::sku, Function.identity()));

        Optional<String> rejection = ReservationPolicy.rejectionReason(wanted, levels);
        if (rejection.isPresent()) {
            reservations.create(orderId, ReservationStatus.REJECTED, List.of());
            outbox.publish(Topics.INVENTORY_RESERVATION_REJECTED, orderId.toString(),
                    new StockReservationRejected(orderId, rejection.get()), corr);
            log.info("Reserva do pedido {} recusada: {}", orderId, rejection.get());
            return;
        }

        List<ReservedLine> lines = new ArrayList<>();
        wanted.forEach((sku, qty) -> {
            stock.addReserved(sku, qty);
            lines.add(new ReservedLine(sku, qty));
        });
        reservations.create(orderId, ReservationStatus.RESERVED, lines);
        outbox.publish(Topics.INVENTORY_STOCK_RESERVED, orderId.toString(), new StockReserved(orderId), corr);
    }

    @Transactional
    @Override
    public void commit(EventEnvelope envelope, CommitStockRequested cmd) {
        if (!inbox.markProcessed(envelope.messageId(), "inventory-service:commit")) {
            return;
        }
        Optional<ReservationStatus> status = reservations.status(cmd.orderId());
        if (status.isEmpty() || status.get() != ReservationStatus.RESERVED) {
            log.info("commit do pedido {} ignorado (estado: {})", cmd.orderId(), status.orElse(null));
            return;
        }
        for (ReservedLine line : reservations.lines(cmd.orderId())) {
            stock.commit(line.sku(), line.quantity());
        }
        reservations.setStatus(cmd.orderId(), ReservationStatus.COMMITTED);
    }

    /** COMPENSACAO da SAGA. Sempre responde StockReleased, para o pedido poder fechar. */
    @Transactional
    @Override
    public void release(EventEnvelope envelope, ReleaseStockRequested cmd) {
        if (!inbox.markProcessed(envelope.messageId(), "inventory-service:release")) {
            return;
        }
        UUID orderId = cmd.orderId();
        Optional<ReservationStatus> status = reservations.status(orderId);

        if (status.isEmpty()) {
            // Liberacao chegou antes da reserva: grava lapide para recusar a reserva tardia.
            reservations.create(orderId, ReservationStatus.RELEASED, List.of());
        } else if (status.get() == ReservationStatus.RESERVED) {
            for (ReservedLine line : reservations.lines(orderId)) {
                stock.addReserved(line.sku(), -line.quantity());
            }
            reservations.setStatus(orderId, ReservationStatus.RELEASED);
        } else if (status.get() == ReservationStatus.COMMITTED) {
            log.warn("release do pedido {} recusado: estoque ja baixado (use restock)", orderId);
            return;
        }
        outbox.publish(Topics.INVENTORY_STOCK_RELEASED, orderId.toString(), new StockReleased(orderId), Correlation.from(envelope));
    }

    /** Devolucao chegou ao vendedor: a mercadoria volta para o estoque. Uma vez por (pedido, vendedor). */
    @Transactional
    @Override
    public void restock(EventEnvelope envelope, RestockRequested cmd) {
        if (!inbox.markProcessed(envelope.messageId(), "inventory-service:restock")) {
            return;
        }
        if (cmd.orderId() == null || cmd.sellerId() == null || cmd.lines() == null) {
            throw new InvalidMessageException("restock-requested incompleto");
        }
        if (!reservations.registerRestock(cmd.orderId(), cmd.sellerId())) {
            log.info("Reposicao do pedido {} / vendedor {} ja feita", cmd.orderId(), cmd.sellerId());
            return;
        }
        for (OrderLine line : cmd.lines()) {
            stock.restock(line.sku(), line.quantity());
        }
        outbox.publish(Topics.INVENTORY_STOCK_RESTOCKED, cmd.orderId().toString(),
                new StockRestocked(cmd.orderId(), cmd.sellerId()), Correlation.from(envelope));
    }

    private static List<ReservedLine> toLines(List<OrderLine> lines) {
        return lines.stream().map(l -> new ReservedLine(l.sku(), l.quantity())).toList();
    }

    private void replyForExisting(UUID orderId, ReservationStatus status, Correlation corr) {
        switch (status) {
            case RESERVED, COMMITTED ->
                outbox.publish(Topics.INVENTORY_STOCK_RESERVED, orderId.toString(), new StockReserved(orderId), corr);
            case REJECTED, RELEASED ->
                outbox.publish(Topics.INVENTORY_RESERVATION_REJECTED, orderId.toString(),
                        new StockReservationRejected(orderId, "RESERVATION_" + status), corr);
        }
    }
}
