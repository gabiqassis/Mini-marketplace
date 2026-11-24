package br.com.marketplace.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida: reservas por pedido. */
public interface ReservationRepository {

    Optional<ReservationStatus> status(UUID orderId);

    /** INSERT puro: a PK recusa segunda reserva do mesmo pedido. */
    void create(UUID orderId, ReservationStatus status, List<ReservedLine> lines);

    List<ReservedLine> lines(UUID orderId);

    void setStatus(UUID orderId, ReservationStatus status);

    /** @return false se a reposicao deste (pedido, vendedor) ja foi feita. */
    boolean registerRestock(UUID orderId, String sellerId);
}
