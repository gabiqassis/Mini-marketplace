package br.com.marketplace.contracts;

import java.util.List;

/**
 * Topicos Kafka (FATOS e passos da SAGA). A fonte da verdade e docs/contracts/asyncapi.yaml;
 * TopicsMatchContractsTest quebra o build se esta classe e o contrato divergirem.
 * Convencao: contexto.fato.vN. Cada topico tem um irmao .DLT.
 */
public final class Topics {

    private Topics() {}

    // vendedor (via SQS -> ingestion) -> catalogo, nota fiscal, notificacao
    public static final String SELLER_PROFILE_UPDATED = "seller.profile-updated.v1";
    public static final String SELLER_ITEM_PUBLISHED = "seller.item-published.v1";

    // catalogo -> inventario, pedidos
    public static final String CATALOG_ITEM_UPSERTED = "catalog.item-upserted.v1";

    // SAGA: pedidos -> inventario
    public static final String INVENTORY_RESERVE_REQUESTED = "inventory.reserve-requested.v1";
    public static final String INVENTORY_COMMIT_REQUESTED = "inventory.commit-requested.v1";
    public static final String INVENTORY_RELEASE_REQUESTED = "inventory.release-requested.v1";
    public static final String INVENTORY_RESTOCK_REQUESTED = "inventory.restock-requested.v1";

    // inventario -> pedidos
    public static final String INVENTORY_STOCK_RESERVED = "inventory.stock-reserved.v1";
    public static final String INVENTORY_RESERVATION_REJECTED = "inventory.reservation-rejected.v1";
    public static final String INVENTORY_STOCK_RELEASED = "inventory.stock-released.v1";
    public static final String INVENTORY_STOCK_RESTOCKED = "inventory.stock-restocked.v1";

    // SAGA: pedidos -> pagamentos
    public static final String PAYMENT_AUTHORIZE_REQUESTED = "payment.authorize-requested.v1";
    public static final String PAYMENT_CAPTURE_REQUESTED = "payment.capture-requested.v1";
    public static final String PAYMENT_REFUND_REQUESTED = "payment.refund-requested.v1";

    // pagamentos -> pedidos
    public static final String PAYMENT_AUTHORIZED = "payment.authorized.v1";
    public static final String PAYMENT_DECLINED = "payment.declined.v1";
    public static final String PAYMENT_CAPTURED = "payment.captured.v1";
    public static final String PAYMENT_REFUNDED = "payment.refunded.v1";

    // pedidos -> todos (pos-venda)
    public static final String ORDER_CONFIRMED = "order.confirmed.v1";
    public static final String ORDER_STATUS_CHANGED = "order.status-changed.v1";

    // nota fiscal -> pedidos, envio, notificacao
    public static final String INVOICE_ISSUED = "invoice.issued.v1";
    public static final String INVOICE_REJECTED = "invoice.rejected.v1";

    // envio -> pedidos, notificacao
    public static final String SHIPMENT_STATUS_CHANGED = "shipping.status-changed.v1";

    public static final List<String> ALL = List.of(
            SELLER_PROFILE_UPDATED,
            SELLER_ITEM_PUBLISHED,
            CATALOG_ITEM_UPSERTED,
            INVENTORY_RESERVE_REQUESTED,
            INVENTORY_COMMIT_REQUESTED,
            INVENTORY_RELEASE_REQUESTED,
            INVENTORY_RESTOCK_REQUESTED,
            INVENTORY_STOCK_RESERVED,
            INVENTORY_RESERVATION_REJECTED,
            INVENTORY_STOCK_RELEASED,
            INVENTORY_STOCK_RESTOCKED,
            PAYMENT_AUTHORIZE_REQUESTED,
            PAYMENT_CAPTURE_REQUESTED,
            PAYMENT_REFUND_REQUESTED,
            PAYMENT_AUTHORIZED,
            PAYMENT_DECLINED,
            PAYMENT_CAPTURED,
            PAYMENT_REFUNDED,
            ORDER_CONFIRMED,
            ORDER_STATUS_CHANGED,
            INVOICE_ISSUED,
            INVOICE_REJECTED,
            SHIPMENT_STATUS_CHANGED);
}
