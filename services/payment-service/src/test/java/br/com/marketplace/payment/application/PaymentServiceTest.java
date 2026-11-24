package br.com.marketplace.payment.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.AuthorizePaymentRequested;
import br.com.marketplace.payment.domain.GatewayResult;
import br.com.marketplace.payment.domain.Payment;
import br.com.marketplace.payment.domain.PaymentGateway;
import br.com.marketplace.payment.domain.PaymentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentRecorder recorder = mock(PaymentRecorder.class);
    private final PaymentService service = new PaymentService(payments, gateway, recorder);

    private final UUID orderId = UUID.randomUUID();
    private final EventEnvelope env = new EventEnvelope(UUID.randomUUID(), "X", Instant.now(), orderId.toString(), null, "o", null);

    @Test
    void usesAStableIdempotencyKeyPerOrder() {
        when(payments.findByOrder(orderId)).thenReturn(Optional.empty());
        when(gateway.authorize(any(), any(), any())).thenReturn(GatewayResult.approved("r"));

        service.authorize(env, new AuthorizePaymentRequested(orderId, "buyer", BigDecimal.TEN));

        verify(gateway).authorize(eq("auth:" + orderId), eq("buyer"), any());
        verify(recorder).recordAuthorization(eq(env), any(), eq(GatewayResult.approved("r")));
    }

    @Test
    void secondCommandForSameOrderDoesNotChargeAgain() {
        when(payments.findByOrder(orderId)).thenReturn(Optional.of(Payment.authorizing(orderId, "b", BigDecimal.TEN)));

        service.authorize(env, new AuthorizePaymentRequested(orderId, "buyer", BigDecimal.TEN));

        verify(gateway, never()).authorize(any(), any(), any());
    }

    @Test
    void reconciliationAppliesTheResultTheGatewayAlreadyHad() {
        Payment unknown = Payment.authorizing(orderId, "buyer-timeout", BigDecimal.TEN);
        when(payments.findAuthorizingOlderThan(any(), anyInt())).thenReturn(List.of(unknown));
        when(payments.incrementAttempts(unknown.id())).thenReturn(1);
        when(gateway.lookup("auth:" + orderId)).thenReturn(Optional.of(GatewayResult.approved("r")));

        service.reconcile(Duration.ofSeconds(5), 5);

        verify(recorder).resolveUnknown(unknown, GatewayResult.approved("r"));
        verify(gateway, never()).authorize(any(), any(), any());
    }

    @Test
    void reconciliationResendsWithSameKeyWhenGatewayNeverReceived() {
        Payment unknown = Payment.authorizing(orderId, "buyer", BigDecimal.TEN);
        when(payments.findAuthorizingOlderThan(any(), anyInt())).thenReturn(List.of(unknown));
        when(payments.incrementAttempts(unknown.id())).thenReturn(1);
        when(gateway.lookup(any())).thenReturn(Optional.empty());
        when(gateway.authorize(eq("auth:" + orderId), any(), any())).thenReturn(GatewayResult.approved("r2"));

        service.reconcile(Duration.ofSeconds(5), 5);

        verify(recorder).resolveUnknown(unknown, GatewayResult.approved("r2"));
    }

    @Test
    void reconciliationGivesUpAfterMaxAttempts() {
        Payment unknown = Payment.authorizing(orderId, "buyer", BigDecimal.TEN);
        when(payments.findAuthorizingOlderThan(any(), anyInt())).thenReturn(List.of(unknown));
        when(payments.incrementAttempts(unknown.id())).thenReturn(5);
        when(gateway.lookup(any())).thenReturn(Optional.empty());
        when(gateway.authorize(any(), any(), any())).thenReturn(GatewayResult.unknown());

        service.reconcile(Duration.ofSeconds(5), 5);

        verify(recorder).resolveUnknown(eq(unknown), argThat(r -> "GATEWAY_NO_RESPONSE".equals(r.declineCode())));
    }
}
