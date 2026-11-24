package br.com.marketplace.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.marketplace.audit.domain.AuditEvent;
import br.com.marketplace.messaging.InvalidMessageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditIndexerTest {

    private final AuditIndexer indexer = new AuditIndexer(new ObjectMapper());

    @Test
    void indexesOrderSkusAndCorrelationFromNestedLines() {
        UUID msg = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        String json = """
                {"messageId":"%s","type":"OrderConfirmed","occurredAt":"2026-11-23T10:00:00Z","correlationId":"%s",
                 "producer":"order-service",
                 "payload":{"orderId":"%s","sellers":[{"sellerId":"loja","lines":[{"sku":"A"},{"sku":"B"}]}]}}
                """.formatted(msg, order, order);

        AuditEvent e = indexer.index("order.confirmed.v1", 2, 99, json);

        assertThat(e.messageId()).isEqualTo(msg);
        assertThat(e.orderId()).isEqualTo(order);
        assertThat(e.skus()).containsExactly("A", "B");
        assertThat(e.correlationId()).isEqualTo(order.toString());
        assertThat(e.partition()).isEqualTo(2);
        assertThat(e.offset()).isEqualTo(99);
    }

    @Test
    void sellerSideEventsAreIndexedBySkuAndSeller() {
        String json = """
                {"messageId":"%s","type":"SellerItemPublished","occurredAt":"2026-11-23T09:00:00Z","correlationId":"batch-1",
                 "payload":{"sellerId":"loja","sku":"A","stockQty":5}}
                """.formatted(UUID.randomUUID());

        AuditEvent e = indexer.index("seller.item-published.v1", 0, 1, json);

        assertThat(e.orderId()).isNull();
        assertThat(e.sellerId()).isEqualTo("loja");
        assertThat(e.skus()).containsExactly("A");
    }

    @Test
    void garbageGoesToDeadLetter() {
        assertThatThrownBy(() -> indexer.index("t", 0, 0, "nao-json")).isInstanceOf(InvalidMessageException.class);
        assertThatThrownBy(() -> indexer.index("t", 0, 0, "{\"type\":\"x\"}")).isInstanceOf(InvalidMessageException.class);
    }
}
