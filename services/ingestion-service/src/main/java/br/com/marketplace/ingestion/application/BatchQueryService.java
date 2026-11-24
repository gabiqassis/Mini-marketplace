package br.com.marketplace.ingestion.application;

import br.com.marketplace.ingestion.domain.BatchReport;
import br.com.marketplace.ingestion.domain.BatchRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BatchQueryService {

    private final BatchRepository batches;

    public BatchQueryService(BatchRepository batches) {
        this.batches = batches;
    }

    public Optional<BatchReport> find(String sellerId, String batchId) {
        return batches.find(sellerId, batchId);
    }
}
