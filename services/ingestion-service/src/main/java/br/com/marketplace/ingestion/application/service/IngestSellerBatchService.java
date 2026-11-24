package br.com.marketplace.ingestion.application.service;

import br.com.marketplace.ingestion.application.port.in.IngestSellerBatchUseCase;
import br.com.marketplace.ingestion.application.port.in.IngestSellerBatchUseCase.Outcome;
import br.com.marketplace.contracts.Events.SellerItemPublished;
import br.com.marketplace.contracts.Events.SellerProfileUpdated;
import br.com.marketplace.contracts.Topics;
import br.com.marketplace.ingestion.domain.BatchReport;
import br.com.marketplace.ingestion.application.port.out.BatchRepository;
import br.com.marketplace.ingestion.domain.BatchValidator;
import br.com.marketplace.ingestion.domain.ItemVerdict;
import br.com.marketplace.ingestion.domain.SellerBatch;
import br.com.marketplace.messaging.Correlation;
import br.com.marketplace.messaging.OutboxRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: o vendedor manda um lote (via SQS ou API).
 * Idempotente por (sellerId, batchId): reenvio devolve o mesmo relatorio sem publicar de novo.
 * correlationId = batchId: o audit-service liga o lote aos itens, ao estoque e as vendas.
 */
@Service
public class IngestSellerBatchService implements IngestSellerBatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestSellerBatchService.class);

    private final BatchValidator validator = new BatchValidator();
    private final BatchRepository batches;
    private final OutboxRepository outbox;

    public IngestSellerBatchService(BatchRepository batches, OutboxRepository outbox) {
        this.batches = batches;
        this.outbox = outbox;
    }

    /** @throws br.com.marketplace.ingestion.domain.InvalidBatchException cabecalho invalido */
    @Transactional
    @Override
    public Outcome ingest(SellerBatch batch, String source) {
        validator.validateHeader(batch);

        var existing = batches.find(batch.sellerId(), batch.batchId());
        if (existing.isPresent()) {
            log.info("Lote {} do vendedor {} repetido: ignorado", batch.batchId(), batch.sellerId());
            return new Outcome(existing.get(), true);
        }

        List<ItemVerdict> verdicts = validator.validateItems(batch);
        BatchReport report = toReport(batch, source, verdicts);
        if (!batches.saveIfAbsent(report)) {
            // corrida entre duas entregas do mesmo lote: a outra ganhou
            return new Outcome(batches.find(batch.sellerId(), batch.batchId()).orElse(report), true);
        }

        Correlation correlation = Correlation.root(batch.batchId());
        outbox.publish(Topics.SELLER_PROFILE_UPDATED, batch.sellerId(),
                new SellerProfileUpdated(batch.sellerId(), batch.seller().legalName(), batch.seller().cnpj(),
                        batch.seller().email(), batch.batchId()),
                correlation);
        for (ItemVerdict v : verdicts) {
            if (v.accepted()) {
                SellerBatch.Item i = v.item();
                outbox.publish(Topics.SELLER_ITEM_PUBLISHED, i.sku(),
                        new SellerItemPublished(batch.sellerId(), i.sku(), i.title().strip(), i.price(), i.stockQty(),
                                i.sequence(), batch.batchId()),
                        correlation);
            }
        }
        log.info("Lote {} do vendedor {}: {} aceito(s), {} rejeitado(s)",
                batch.batchId(), batch.sellerId(), report.accepted(), report.rejected());
        return new Outcome(report, false);
    }

    private static BatchReport toReport(SellerBatch batch, String source, List<ItemVerdict> verdicts) {
        List<BatchReport.ItemResult> items = verdicts.stream()
                .map(v -> new BatchReport.ItemResult(
                        v.item() == null ? null : v.item().sku(), v.accepted() ? "ACCEPTED" : "REJECTED", v.reason()))
                .toList();
        int accepted = (int) verdicts.stream().filter(ItemVerdict::accepted).count();
        return new BatchReport(batch.sellerId(), batch.batchId(), source, accepted, verdicts.size() - accepted, items);
    }
}
