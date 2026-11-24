package br.com.marketplace.order.application;

import br.com.marketplace.contracts.Events.OrderLine;
import br.com.marketplace.contracts.Events.ReserveStockRequested;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.OutboxRepository;
import br.com.marketplace.order.application.OrderExceptions.IdempotencyConflictException;
import br.com.marketplace.order.application.OrderExceptions.InvalidOrderException;
import br.com.marketplace.order.application.OrderExceptions.TooManyRequestsException;
import br.com.marketplace.order.application.OrderExceptions.UnknownProductException;
import br.com.marketplace.order.domain.IdempotentOrder;
import br.com.marketplace.order.domain.Order;
import br.com.marketplace.order.domain.OrderItem;
import br.com.marketplace.order.domain.OrderRepository;
import br.com.marketplace.order.domain.ProductSnapshot;
import br.com.marketplace.order.domain.ProductSnapshotRepository;
import br.com.marketplace.order.domain.RateLimiter;
import br.com.marketplace.order.domain.ShippingAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: comprador faz um pedido. Inicia a SAGA gravando o comando no outbox.
 * Idempotencia: Idempotency-Key (UNIQUE no banco) + hash do corpo (mesma chave, outro corpo = 422).
 * Preco e vendedor vem da copia local do catalogo, nunca do cliente.
 */
@Service
public class PlaceOrderService {

    public record Line(String sku, int quantity) {}

    public record Command(String idempotencyKey, String buyerId, String buyerEmail, ShippingAddress address, List<Line> lines) {}

    public record Result(Order order, boolean created) {}

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final OrderRepository orders;
    private final ProductSnapshotRepository products;
    private final OutboxRepository outbox;
    private final RateLimiter rateLimiter;

    public PlaceOrderService(
            OrderRepository orders, ProductSnapshotRepository products, OutboxRepository outbox, RateLimiter rateLimiter) {
        this.orders = orders;
        this.products = products;
        this.outbox = outbox;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public Result place(Command cmd) {
        validate(cmd);
        Map<String, Integer> merged = merge(cmd.lines());
        String hash = hash(cmd, merged);

        Optional<IdempotentOrder> existing = orders.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        if (!rateLimiter.tryAcquire("orders:" + cmd.buyerId())) {
            throw new TooManyRequestsException();
        }

        Map<String, ProductSnapshot> snapshots = products.findAll(merged.keySet());
        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : merged.entrySet()) {
            ProductSnapshot p = snapshots.get(e.getKey());
            if (p == null) {
                throw new UnknownProductException(e.getKey());
            }
            items.add(new OrderItem(p.sku(), p.sellerId(), p.title(), e.getValue(), p.price()));
        }

        Order order = Order.place(UUID.randomUUID(), cmd.buyerId(), cmd.buyerEmail(), cmd.address(), items);
        if (!orders.insertIfAbsent(order, cmd.idempotencyKey(), hash)) {
            // Outra requisicao com a mesma chave ganhou a corrida: devolve o pedido dela.
            return replay(orders.findByIdempotencyKey(cmd.idempotencyKey()).orElseThrow(), hash);
        }

        List<OrderLine> lines = items.stream().map(i -> new OrderLine(i.sku(), i.quantity())).toList();
        outbox.publish(Topics.INVENTORY_RESERVE_REQUESTED, order.id().toString(),
                new ReserveStockRequested(order.id(), lines), Correlation.root(order.id()));
        return new Result(order, true);
    }

    private static void validate(Command cmd) {
        if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank() || cmd.idempotencyKey().length() > 100) {
            throw new InvalidOrderException("Idempotency-Key obrigatoria (ate 100 caracteres)");
        }
        if (cmd.buyerId() == null || cmd.buyerId().isBlank()) {
            throw new InvalidOrderException("buyerId obrigatorio");
        }
        if (cmd.buyerEmail() == null || !EMAIL.matcher(cmd.buyerEmail()).matches()) {
            throw new InvalidOrderException("buyerEmail invalido");
        }
        if (cmd.address() == null) {
            throw new InvalidOrderException("endereco de entrega obrigatorio");
        }
        if (cmd.lines() == null || cmd.lines().isEmpty()) {
            throw new InvalidOrderException("Pedido sem itens");
        }
    }

    private static Map<String, Integer> merge(List<Line> lines) {
        Map<String, Integer> merged = new TreeMap<>();
        for (Line line : lines) {
            if (line.sku() == null || line.sku().isBlank()) {
                throw new InvalidOrderException("sku obrigatorio");
            }
            if (line.quantity() <= 0 || line.quantity() > 100) {
                throw new InvalidOrderException("quantity deve estar entre 1 e 100");
            }
            merged.merge(line.sku(), line.quantity(), Integer::sum);
        }
        return merged;
    }

    private static Result replay(IdempotentOrder existing, String hash) {
        if (!existing.requestHash().equals(hash)) {
            throw new IdempotencyConflictException();
        }
        return new Result(existing.order(), false);
    }

    static String hash(Command cmd, Map<String, Integer> merged) {
        StringBuilder canonical = new StringBuilder(cmd.buyerId()).append('|').append(cmd.buyerEmail())
                .append('|').append(cmd.address());
        merged.forEach((sku, qty) -> canonical.append('|').append(sku).append(':').append(qty));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
