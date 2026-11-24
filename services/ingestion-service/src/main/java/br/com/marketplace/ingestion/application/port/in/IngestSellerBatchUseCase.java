package br.com.marketplace.ingestion.application.port.in;

import br.com.marketplace.ingestion.domain.BatchReport;
import br.com.marketplace.ingestion.domain.SellerBatch;

/** Porta de entrada: contrato do caso de uso, implementado em IngestSellerBatchService. */
public interface IngestSellerBatchUseCase {

    record Outcome(BatchReport report, boolean duplicate) {}

    Outcome ingest(SellerBatch batch, String source);
}
