package br.com.marketplace.ingestion.adapter.in.messaging;

import br.com.marketplace.ingestion.application.port.in.IngestSellerBatchUseCase;
import br.com.marketplace.ingestion.domain.InvalidBatchException;
import br.com.marketplace.ingestion.domain.SellerBatch;
import br.com.marketplace.messaging.LogContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * Consumidor SQS em uma VIRTUAL THREAD (long polling de 10 s, lotes de ate 10 mensagens).
 *
 * <ul>
 *   <li>Sucesso ou lote repetido: apaga a mensagem.</li>
 *   <li>Lote invalido (JSON quebrado, cabecalho errado): manda para a DLQ com o motivo e apaga.</li>
 *   <li>Falha transitoria (banco fora): NAO apaga. Volta apos o visibility timeout; depois de
 *       maxReceiveCount tentativas o proprio SQS move para a DLQ (redrive policy).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "marketplace.sqs.enabled", havingValue = "true", matchIfMissing = true)
class SqsSellerBatchPoller implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SqsSellerBatchPoller.class);

    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final IngestSellerBatchUseCase ingest;
    private final String queueName;
    private final String dlqName;
    private volatile boolean running;
    private Thread worker;

    SqsSellerBatchPoller(
            SqsClient sqs,
            ObjectMapper mapper,
            IngestSellerBatchUseCase ingest,
            @Value("${marketplace.sqs.queue:seller-batches}") String queueName,
            @Value("${marketplace.sqs.dlq:seller-batches-dlq}") String dlqName) {
        this.sqs = sqs;
        this.mapper = mapper;
        this.ingest = ingest;
        this.queueName = queueName;
        this.dlqName = dlqName;
    }

    @Override
    public void start() {
        running = true;
        worker = Thread.ofVirtual().name("sqs-seller-batches").start(this::loop);
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        String queueUrl = null;
        String dlqUrl = null;
        while (running) {
            try {
                if (queueUrl == null) {
                    queueUrl = sqs.getQueueUrl(b -> b.queueName(queueName)).queueUrl();
                    dlqUrl = sqs.getQueueUrl(b -> b.queueName(dlqName)).queueUrl();
                    log.info("Consumindo SQS {}", queueUrl);
                }
                String url = queueUrl;
                for (Message m : sqs.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(10))
                        .messages()) {
                    handle(url, dlqUrl, m);
                }
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                log.warn("SQS indisponivel, tentando de novo em 3 s: {}", e.getMessage());
                queueUrl = null;
                sleepQuietly(3);
            }
        }
    }

    private void handle(String queueUrl, String dlqUrl, Message m) {
        try {
            SellerBatch batch = mapper.readValue(m.body(), SellerBatch.class);
            try (var ignored = LogContext.of(batch.batchId() == null ? m.messageId() : batch.batchId())) {
                IngestSellerBatchUseCase.Outcome outcome = ingest.ingest(batch, "SQS");
                log.info("Lote SQS {} processado (repetido={})", batch.batchId(), outcome.duplicate());
            }
            delete(queueUrl, m);
        } catch (JsonProcessingException | InvalidBatchException e) {
            log.error("Lote invalido na mensagem SQS {}: {} -> DLQ", m.messageId(), e.getMessage());
            sqs.sendMessage(b -> b.queueUrl(dlqUrl).messageBody(m.body()).messageAttributes(Map.of(
                    "failureReason", MessageAttributeValue.builder().dataType("String").stringValue(String.valueOf(e.getMessage())).build())));
            delete(queueUrl, m);
        } catch (RuntimeException e) {
            log.warn("Falha transitoria no lote SQS {} (sera reentregue): {}", m.messageId(), e.getMessage());
        }
    }

    private void delete(String queueUrl, Message m) {
        sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(m.receiptHandle()));
    }

    private static void sleepQuietly(long seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
