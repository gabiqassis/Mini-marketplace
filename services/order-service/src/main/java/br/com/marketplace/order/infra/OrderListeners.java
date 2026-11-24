package br.com.marketplace.order.infra;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.CatalogItemUpserted;
import br.com.marketplace.contracts.Events.InvoiceIssued;
import br.com.marketplace.contracts.Events.InvoiceRejected;
import br.com.marketplace.contracts.Events.PaymentAuthorized;
import br.com.marketplace.contracts.Events.PaymentDeclined;
import br.com.marketplace.contracts.Events.PaymentRefunded;
import br.com.marketplace.contracts.Events.ShipmentStatusChanged;
import br.com.marketplace.contracts.Events.StockReleased;
import br.com.marketplace.contracts.Events.StockReservationRejected;
import br.com.marketplace.contracts.Events.StockReserved;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.messaging.Envelopes;
import br.com.marketplace.messaging.LogContext;
import br.com.marketplace.order.application.FulfillmentCoordinator;
import br.com.marketplace.order.application.ProductSnapshotService;
import br.com.marketplace.order.application.SagaCoordinator;
import java.util.function.BiConsumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Adaptadores de entrada: le o envelope, abre o contexto de log e delega para o caso de uso. */
@Component
class OrderListeners {

    private final Envelopes envelopes;
    private final SagaCoordinator saga;
    private final FulfillmentCoordinator fulfillment;
    private final ProductSnapshotService snapshots;

    OrderListeners(Envelopes envelopes, SagaCoordinator saga, FulfillmentCoordinator fulfillment, ProductSnapshotService snapshots) {
        this.envelopes = envelopes;
        this.saga = saga;
        this.fulfillment = fulfillment;
        this.snapshots = snapshots;
    }

    private <T> void dispatch(String json, Class<T> type, BiConsumer<EventEnvelope, T> handler) {
        EventEnvelope env = envelopes.read(json);
        try (var ignored = LogContext.of(env)) {
            handler.accept(env, envelopes.payload(env, type));
        }
    }

    @KafkaListener(topics = Topics.CATALOG_ITEM_UPSERTED)
    void onItemUpserted(String json) {
        dispatch(json, CatalogItemUpserted.class, snapshots::onItemUpserted);
    }

    @KafkaListener(topics = Topics.INVENTORY_STOCK_RESERVED)
    void onStockReserved(String json) {
        dispatch(json, StockReserved.class, saga::onStockReserved);
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVATION_REJECTED)
    void onReservationRejected(String json) {
        dispatch(json, StockReservationRejected.class, saga::onReservationRejected);
    }

    @KafkaListener(topics = Topics.PAYMENT_AUTHORIZED)
    void onPaymentAuthorized(String json) {
        dispatch(json, PaymentAuthorized.class, saga::onPaymentAuthorized);
    }

    @KafkaListener(topics = Topics.PAYMENT_DECLINED)
    void onPaymentDeclined(String json) {
        dispatch(json, PaymentDeclined.class, saga::onPaymentDeclined);
    }

    @KafkaListener(topics = Topics.INVENTORY_STOCK_RELEASED)
    void onStockReleased(String json) {
        dispatch(json, StockReleased.class, saga::onStockReleased);
    }

    @KafkaListener(topics = Topics.INVOICE_ISSUED)
    void onInvoiceIssued(String json) {
        dispatch(json, InvoiceIssued.class, fulfillment::onInvoiceIssued);
    }

    @KafkaListener(topics = Topics.INVOICE_REJECTED)
    void onInvoiceRejected(String json) {
        dispatch(json, InvoiceRejected.class, fulfillment::onInvoiceRejected);
    }

    @KafkaListener(topics = Topics.SHIPMENT_STATUS_CHANGED)
    void onShipmentStatusChanged(String json) {
        dispatch(json, ShipmentStatusChanged.class, fulfillment::onShipmentStatusChanged);
    }

    @KafkaListener(topics = Topics.PAYMENT_REFUNDED)
    void onPaymentRefunded(String json) {
        dispatch(json, PaymentRefunded.class, fulfillment::onPaymentRefunded);
    }
}
