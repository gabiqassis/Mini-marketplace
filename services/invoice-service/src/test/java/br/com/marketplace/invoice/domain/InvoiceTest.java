package br.com.marketplace.invoice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    private final Invoice issuing = new Invoice(UUID.randomUUID(), UUID.randomUUID(), "loja", 1, 10, BigDecimal.TEN,
            Invoice.InvoiceStatus.ISSUING, null, null, null, null);

    @Test
    void authorizedKeepsNumberAndGetsKey() {
        Invoice done = issuing.authorized("k", "p");
        assertThat(done.isFinished()).isTrue();
        assertThat(done.number()).isEqualTo(10);
        assertThat(done.accessKey()).isEqualTo("k");
    }

    @Test
    void hugeExternalErrorMessageIsShortenedSoTheFailureDoesNotCauseAnotherFailure() {
        // bug real encontrado no E2E: mensagem do AWS SDK estourava a coluna e o "desistir" falhava
        Invoice failed = issuing.failed("x".repeat(5_000));
        assertThat(failed.rejectionReason()).hasSize(Invoice.MAX_REASON).endsWith("...");
        assertThat(failed.rejectionCode()).isEqualTo("TECHNICAL");
        assertThat(Invoice.shorten(null)).isNull();
        assertThat(Invoice.shorten("curto")).isEqualTo("curto");
    }
}
