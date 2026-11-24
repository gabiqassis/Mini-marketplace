package br.com.marketplace.invoice.application;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.IssueInvoiceCommand;
import br.com.marketplace.invoice.domain.Invoice;
import br.com.marketplace.invoice.domain.InvoiceIssuer;
import br.com.marketplace.invoice.domain.InvoiceRepository;
import br.com.marketplace.invoice.domain.SellerFiscalProfile;
import br.com.marketplace.invoice.domain.SellerProfileRepository;
import br.com.marketplace.messaging.InvalidMessageException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caso de uso: emitir a NF-e da parte de um vendedor.
 * Duas classes de erro, tratadas de forma diferente:
 * <ul>
 *   <li>REJEICAO FISCAL (CNPJ irregular, valor invalido): definitiva, publica invoice.rejected.</li>
 *   <li>FALHA TECNICA (Lambda/SEFAZ fora): excecao -&gt; RabbitMQ tenta de novo com backoff.</li>
 * </ul>
 * Idempotente: nota ja autorizada/rejeitada para (pedido, vendedor) nao e emitida de novo.
 */
@Service
public class IssueInvoiceUseCase {

    private static final Logger log = LoggerFactory.getLogger(IssueInvoiceUseCase.class);

    private final InvoiceRepository invoices;
    private final SellerProfileRepository sellers;
    private final InvoiceIssuer issuer;
    private final InvoiceRecorder recorder;
    private final Clock clock;

    public IssueInvoiceUseCase(
            InvoiceRepository invoices, SellerProfileRepository sellers, InvoiceIssuer issuer, InvoiceRecorder recorder, Clock clock) {
        this.invoices = invoices;
        this.sellers = sellers;
        this.issuer = issuer;
        this.recorder = recorder;
        this.clock = clock;
    }

    public void issue(EventEnvelope env, IssueInvoiceCommand cmd) {
        if (cmd.orderId() == null || cmd.sellerId() == null || cmd.amount() == null) {
            throw new InvalidMessageException("comando de nota incompleto");
        }
        Optional<Invoice> existing = invoices.find(cmd.orderId(), cmd.sellerId());
        if (existing.isPresent() && existing.get().isFinished()) {
            log.info("Nota do pedido {} / {} ja finalizada ({}): comando repetido ignorado",
                    cmd.orderId(), cmd.sellerId(), existing.get().status());
            return;
        }
        SellerFiscalProfile seller = sellers.find(cmd.sellerId())
                .orElseThrow(() -> new IllegalStateException("dados fiscais do vendedor " + cmd.sellerId() + " ainda nao chegaram"));

        Invoice invoice = recorder.reserve(cmd.orderId(), cmd.sellerId(), cmd.amount());
        InvoiceIssuer.Result result = issuer.issue(new InvoiceIssuer.Request(
                cmd.orderId(), cmd.sellerId(), seller.cnpj(), invoice.series(), invoice.number(), invoice.amount(), Instant.now(clock)));
        recorder.record(env, invoice, result);
        log.info("NF-e {} do pedido {} / {}: {}", invoice.number(), cmd.orderId(), cmd.sellerId(),
                result.authorized() ? "AUTORIZADA" : "REJEITADA " + result.rejectionCode());
    }

    public void giveUp(EventEnvelope env, IssueInvoiceCommand cmd, String reason) {
        recorder.giveUp(env, cmd.orderId(), cmd.sellerId(), reason);
    }
}
