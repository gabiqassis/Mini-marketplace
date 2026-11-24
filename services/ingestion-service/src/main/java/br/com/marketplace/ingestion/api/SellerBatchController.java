package br.com.marketplace.ingestion.api;

import br.com.marketplace.ingestion.application.BatchQueryService;
import br.com.marketplace.ingestion.application.IngestSellerBatchUseCase;
import br.com.marketplace.ingestion.domain.BatchReport;
import br.com.marketplace.ingestion.domain.InvalidBatchException;
import br.com.marketplace.ingestion.domain.SellerBatch;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Alternativa sincrona ao SQS (vendedor pequeno, integracao manual). Mesma regra, mesmo caso de uso. */
@RestController
@RequestMapping("/sellers/{sellerId}/batches")
class SellerBatchController {

    private final IngestSellerBatchUseCase ingest;
    private final BatchQueryService query;

    SellerBatchController(IngestSellerBatchUseCase ingest, BatchQueryService query) {
        this.ingest = ingest;
        this.query = query;
    }

    @PostMapping
    ResponseEntity<BatchReport> submit(@PathVariable String sellerId, @RequestBody SellerBatch body) {
        SellerBatch batch = new SellerBatch(body.batchId(), sellerId, body.seller(), body.items());
        IngestSellerBatchUseCase.Outcome outcome = ingest.ingest(batch, "API");
        return ResponseEntity.status(outcome.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(outcome.report());
    }

    @GetMapping("/{batchId}")
    ResponseEntity<BatchReport> get(@PathVariable String sellerId, @PathVariable String batchId) {
        return query.find(sellerId, batchId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(InvalidBatchException.class)
    ProblemDetail invalid(InvalidBatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
