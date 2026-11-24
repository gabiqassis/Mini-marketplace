package br.com.marketplace.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida: pedidos. */
public interface OrderRepository {

    /** INSERT ... ON CONFLICT (idempotency_key) DO NOTHING. @return false se a chave ja existia. */
    boolean insertIfAbsent(Order order, String idempotencyKey, String requestHash);

    Optional<Order> findById(UUID id);

    /** SELECT ... FOR UPDATE: serializa os eventos do mesmo pedido vindos de topicos diferentes. */
    Optional<Order> lockById(UUID id);

    Optional<IdempotentOrder> findByIdempotencyKey(String idempotencyKey);

    /** UPDATE guardado: so muda se o status atual for o esperado. @return false se outro evento ganhou. */
    boolean transition(UUID id, OrderStatus from, OrderStatus to, String reason);

    void markCaptureRequested(UUID id);

    List<UUID> findStaleIds(OrderStatus status, Instant olderThan, int limit);
}
