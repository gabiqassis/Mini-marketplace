package br.com.marketplace.invoice.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.IssueInvoiceCommand;
import br.com.marketplace.invoice.domain.Invoice;
import br.com.marketplace.invoice.domain.InvoiceIssuer;
import br.com.marketplace.invoice.domain.InvoiceRepository;
import br.com.marketplace.invoice.domain.SellerFiscalProfile;
import br.com.marketplace.invoice.domain.SellerProfileRepository;
import br.com.marketplace.messaging.InvalidMessageException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IssueInvoiceUseCaseTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final SellerProfileRepository sellers = mock(SellerProfileRepository.class);
    private final InvoiceIssuer issuer = mock(InvoiceIssuer.class);
    private final InvoiceRecorder recorder = mock(InvoiceRecorder.class);
    private final IssueInvoiceUseCase useCase = new IssueInvoiceUseCase(invoices, sellers, issuer, recorder,
            Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC));

    private final UUID orderId = UUID.randomUUID();
    private final EventEnvelope env = new EventEnvelope(UUID.randomUUID(), "X", Instant.now(), orderId.toString(), null, "o", null);
    private final IssueInvoiceCommand cmd = new IssueInvoiceCommand(orderId, "loja", "buyer", null, new BigDecimal("50.00"), List.of());

    private Invoice issuing(long number) {
        return new Invoice(UUID.randomUUID(), orderId, "loja", 1, number, new BigDecimal("50.00"), Invoice.InvoiceStatus.ISSUING,
                null, null, null, null);
    }

    @Test
    void reservesNumberCallsSefazAndRecordsResult() {
        when(invoices.find(orderId, "loja")).thenReturn(Optional.empty());
        when(sellers.find("loja")).thenReturn(Optional.of(new SellerFiscalProfile("loja", "Loja", "11222333000181")));
        when(recorder.reserve(orderId, "loja", new BigDecimal("50.00"))).thenReturn(issuing(7));
        InvoiceIssuer.Result ok = new InvoiceIssuer.Result(true, "k", "p", null, null);
        when(issuer.issue(any())).thenReturn(ok);

        useCase.issue(env, cmd);

        ArgumentCaptor<InvoiceIssuer.Request> req = ArgumentCaptor.forClass(InvoiceIssuer.Request.class);
        verify(issuer).issue(req.capture());
        org.assertj.core.api.Assertions.assertThat(req.getValue().number()).isEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(req.getValue().sellerCnpj()).isEqualTo("11222333000181");
        verify(recorder).record(eq(env), any(), eq(ok));
    }

    @Test
    void finishedInvoiceIsNotIssuedAgain() {
        Invoice done = issuing(7).authorized("k", "p");
        when(invoices.find(orderId, "loja")).thenReturn(Optional.of(done));

        useCase.issue(env, cmd);

        verify(issuer, never()).issue(any());
    }

    @Test
    void missingSellerDataIsTransientSoRabbitRetries() {
        when(invoices.find(orderId, "loja")).thenReturn(Optional.empty());
        when(sellers.find("loja")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.issue(env, cmd)).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(InvalidMessageException.class);
    }

    @Test
    void sefazOutageBubblesUpAsTechnicalFailure() {
        when(invoices.find(orderId, "loja")).thenReturn(Optional.empty());
        when(sellers.find("loja")).thenReturn(Optional.of(new SellerFiscalProfile("loja", "Loja", "11222333000181")));
        when(recorder.reserve(any(), any(), any())).thenReturn(issuing(1));
        when(issuer.issue(any())).thenThrow(new InvoiceIssuer.TechnicalFailure("SEFAZ fora", null));

        assertThatThrownBy(() -> useCase.issue(env, cmd)).isInstanceOf(InvoiceIssuer.TechnicalFailure.class);
        verify(recorder, never()).record(any(), any(), any());
    }

    @Test
    void incompleteCommandIsPermanentFailure() {
        assertThatThrownBy(() -> useCase.issue(env, new IssueInvoiceCommand(null, "loja", "b", null, BigDecimal.ONE, List.of())))
                .isInstanceOf(InvalidMessageException.class);
    }
}
