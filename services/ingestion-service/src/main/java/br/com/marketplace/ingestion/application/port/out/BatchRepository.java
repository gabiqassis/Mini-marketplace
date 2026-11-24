package br.com.marketplace.ingestion.application.port.out;

import br.com.marketplace.ingestion.domain.BatchReport;
import java.util.Optional;

/** Porta de saida: registro dos lotes (idempotencia por sellerId + batchId). */
public interface BatchRepository {

    /** @return false se o lote ja existe (reenvio): nada e gravado. */
    boolean saveIfAbsent(BatchReport report);

    Optional<BatchReport> find(String sellerId, String batchId);
}
