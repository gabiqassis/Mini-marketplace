package br.com.marketplace.ingestion.application.service;

import br.com.marketplace.ingestion.application.port.in.BatchQueryUseCase;
import br.com.marketplace.ingestion.domain.BatchReport;
import br.com.marketplace.ingestion.application.port.out.BatchRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BatchQueryService implements BatchQueryUseCase {

    private final BatchRepository batches;

    public BatchQueryService(BatchRepository batches) {
        this.batches = batches;
    }

    @Override
    public Optional<BatchReport> find(String sellerId, String batchId) {
        return batches.find(sellerId, batchId);
    }
}
