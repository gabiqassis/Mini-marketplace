package br.com.marketplace.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private final Payment authorizing = Payment.authorizing(UUID.randomUUID(), "buyer", new BigDecimal("100.00"));

    @Test
    void authorizeThenCaptureTheFullAmount() {
        Payment captured = authorizing.authorized("ref").captured();
        assertThat(captured.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(captured.capturedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void cannotCaptureWhatWasNotAuthorized() {
        assertThat(authorizing.canCapture()).isFalse();
        assertThatThrownBy(authorizing::captured).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> authorizing.declined("X").captured()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refundBeforeCaptureIsAVoidOfTheWholeAuthorization() {
        Payment.RefundOutcome out = authorizing.authorized("ref").refund(new BigDecimal("10"));
        assertThat(out.kind()).isEqualTo(Payment.RefundKind.VOID);
        assertThat(out.amount()).isEqualByComparingTo("100.00");
        assertThat(out.payment().status()).isEqualTo(PaymentStatus.VOIDED);
    }

    @Test
    void partialRefundsNeverExceedWhatWasCaptured() {
        Payment captured = authorizing.authorized("ref").captured();
        Payment.RefundOutcome first = captured.refund(new BigDecimal("30.00"));
        assertThat(first.payment().status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        Payment.RefundOutcome second = first.payment().refund(new BigDecimal("500.00"));
        assertThat(second.amount()).isEqualByComparingTo("70.00");
        assertThat(second.payment().status()).isEqualTo(PaymentStatus.REFUNDED);

        assertThatThrownBy(() -> second.payment().refund(BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void declinedOrUnknownPaymentCannotBeRefunded() {
        assertThatThrownBy(() -> authorizing.refund(BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> authorizing.declined("X").refund(BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> authorizing.authorized("r").captured().refund(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownStateCanOnlyBeResolvedOnce() {
        Payment authorized = authorizing.authorized("ref");
        assertThatThrownBy(() -> authorized.declined("LATE")).isInstanceOf(IllegalStateException.class);
    }
}
