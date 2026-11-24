package br.com.marketplace.ingestion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.marketplace.contracts.Topics;
import br.com.marketplace.ingestion.application.port.in.IngestSellerBatchUseCase;
import br.com.marketplace.ingestion.domain.BatchReport;
import br.com.marketplace.ingestion.application.port.out.BatchRepository;
import br.com.marketplace.ingestion.domain.InvalidBatchException;
import br.com.marketplace.ingestion.domain.SellerBatch;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.OutboxRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IngestSellerBatchServiceTest {

    /** Fake em memoria: testa a regra sem banco. */
    static class InMemoryBatches implements BatchRepository {
        final Map<String, BatchReport> store = new HashMap<>();

        @Override
        public boolean saveIfAbsent(BatchReport r) {
            return store.putIfAbsent(r.sellerId() + "/" + r.batchId(), r) == null;
        }

        @Override
        public Optional<BatchReport> find(String sellerId, String batchId) {
            return Optional.ofNullable(store.get(sellerId + "/" + batchId));
        }
    }

    private final InMemoryBatches batches = new InMemoryBatches();
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final IngestSellerBatchService useCase = new IngestSellerBatchService(batches, outbox);

    private SellerBatch batch() {
        return new SellerBatch("b-1", "loja-x",
                new SellerBatch.SellerProfile("Loja X LTDA", "11222333000181", "loja@x.com"),
                List.of(new SellerBatch.Item("SKU-1", "Camiseta", new BigDecimal("50.00"), 10, 1L),
                        new SellerBatch.Item("SKU-2", "", new BigDecimal("50.00"), 10, 1L)));
    }

    @Test
    void publishesProfileAndOnlyAcceptedItemsCorrelatedByBatch() {
        IngestSellerBatchUseCase.Outcome outcome = useCase.ingest(batch(), "SQS");

        assertThat(outcome.duplicate()).isFalse();
        assertThat(outcome.report().accepted()).isEqualTo(1);
        assertThat(outcome.report().rejected()).isEqualTo(1);
        verify(outbox).publish(eq(Topics.SELLER_PROFILE_UPDATED), eq("loja-x"), any(), eq(Correlation.root("b-1")));
        verify(outbox, times(1)).publish(eq(Topics.SELLER_ITEM_PUBLISHED), eq("SKU-1"), any(), eq(Correlation.root("b-1")));
        verify(outbox, never()).publish(eq(Topics.SELLER_ITEM_PUBLISHED), eq("SKU-2"), any(), any());
    }

    @Test
    void resentBatchIsIdempotent() {
        useCase.ingest(batch(), "SQS");
        IngestSellerBatchUseCase.Outcome second = useCase.ingest(batch(), "SQS");

        assertThat(second.duplicate()).isTrue();
        verify(outbox, times(1)).publish(eq(Topics.SELLER_ITEM_PUBLISHED), any(), any(), any());
    }

    @Test
    void invalidHeaderPublishesNothing() {
        SellerBatch bad = new SellerBatch("b-2", "loja-x", null, batch().items());
        assertThatThrownBy(() -> useCase.ingest(bad, "API")).isInstanceOf(InvalidBatchException.class);
        verify(outbox, never()).publish(any(), any(), any(), any());
    }
}
