package br.com.marketplace.ingestion.application.port.in;

import br.com.marketplace.ingestion.domain.BatchReport;
import java.util.Optional;

/** Porta de entrada: contrato do caso de uso, implementado em BatchQueryService. */
public interface BatchQueryUseCase {

    Optional<BatchReport> find(String sellerId, String batchId);
}
