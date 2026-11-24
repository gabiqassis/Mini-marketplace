package br.com.marketplace.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payloads dos eventos e comandos. Mudou um campo? Mude a spec (asyncapi.yaml) primeiro. */
public final class Events {

    private Events() {}

    // ---------------------------------------------------------------- vendedor
    /** Dados cadastrais e fiscais do vendedor, enviados junto com o lote. */
    public record SellerProfileUpdated(String sellerId, String legalName, String cnpj, String email, String batchId) {}

    /** Item validado pelo ingestion. sequence e monotona por sku (mensagem antiga nunca sobrescreve nova). */
    public record SellerItemPublished(
            String sellerId, String sku, String title, BigDecimal price, int stockQty, long sequence, String batchId) {}

    // ---------------------------------------------------------------- catalogo
    public record CatalogItemUpserted(
            String sellerId, String sku, String title, BigDecimal price, int stockQty, long sequence) {}

    // ---------------------------------------------------------------- inventario
    public record OrderLine(String sku, int quantity) {}

    public record ReserveStockRequested(UUID orderId, List<OrderLine> lines) {}

    public record StockReserved(UUID orderId) {}

    public record StockReservationRejected(UUID orderId, String reason) {}

    public record CommitStockRequested(UUID orderId) {}

    public record ReleaseStockRequested(UUID orderId, String reason) {}

    public record StockReleased(UUID orderId) {}

    /** Mercadoria voltou ao vendedor (devolucao): volta para o estoque. */
    public record RestockRequested(UUID orderId, String sellerId, List<OrderLine> lines, String reason) {}

    public record StockRestocked(UUID orderId, String sellerId) {}

    // ---------------------------------------------------------------- pagamento
    public record AuthorizePaymentRequested(UUID orderId, String buyerId, BigDecimal amount) {}

    public record PaymentAuthorized(UUID orderId, UUID paymentId, BigDecimal amount) {}

    public record PaymentDeclined(UUID orderId, String reason) {}

    public record CapturePaymentRequested(UUID orderId, BigDecimal amount) {}

    public record PaymentCaptured(UUID orderId, UUID paymentId, BigDecimal amount) {}

    /** reference identifica o motivo/parte (ex.: sellerId do envio extraviado, ou "order"). */
    public record RefundPaymentRequested(UUID orderId, String reference, BigDecimal amount, String reason) {}

    /** kind = VOID (autorizacao cancelada, nada foi cobrado) ou REFUND (estorno de valor capturado). */
    public record PaymentRefunded(UUID orderId, String reference, BigDecimal amount, String kind) {}

    // ---------------------------------------------------------------- pedido
    public record Address(String zipCode, String street, String number, String city, String state) {}

    public record SoldLine(String sku, String title, int quantity, BigDecimal unitPrice) {}

    public record SellerPart(String sellerId, BigDecimal amount, List<SoldLine> lines) {}

    public record OrderConfirmed(
            UUID orderId, String buyerId, String buyerEmail, Address address, BigDecimal total, List<SellerPart> sellers) {}

    public record OrderStatusChanged(UUID orderId, String buyerId, String buyerEmail, String status, String reason) {}

    /** Comando RabbitMQ: emitir NF-e da parte de um vendedor. */
    public record IssueInvoiceCommand(
            UUID orderId, String sellerId, String buyerId, Address address, BigDecimal amount, List<SoldLine> lines) {}

    // ---------------------------------------------------------------- nota fiscal
    public record InvoiceIssued(
            UUID orderId, String sellerId, UUID invoiceId, long number, String accessKey, String protocol, BigDecimal amount) {}

    public record InvoiceRejected(UUID orderId, String sellerId, String code, String reason) {}

    // ---------------------------------------------------------------- envio
    public record ShipmentStatusChanged(
            UUID orderId,
            String sellerId,
            UUID shipmentId,
            String trackingCode,
            String status,
            String location,
            Instant occurredAt) {}

    // ---------------------------------------------------------------- notificacao
    /** Job RabbitMQ: enviar a notificacao ja gravada no banco do notification-service. */
    public record SendNotificationJob(UUID notificationId) {}
}
