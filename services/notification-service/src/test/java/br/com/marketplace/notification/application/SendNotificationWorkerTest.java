package br.com.marketplace.notification.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.marketplace.notification.domain.Draft;
import br.com.marketplace.notification.domain.Mailer;
import br.com.marketplace.notification.domain.Notification;
import br.com.marketplace.notification.domain.NotificationRepository;
import br.com.marketplace.notification.domain.SendGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SendNotificationWorkerTest {

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final Mailer mailer = mock(Mailer.class);
    private final SendGuard guard = mock(SendGuard.class);
    private final SendNotificationWorker worker = new SendNotificationWorker(repo, mailer, guard);
    private final UUID id = UUID.randomUUID();

    private Notification with(Notification.Status status) {
        return new Notification(id, UUID.randomUUID(), Draft.Recipient.SELLER, "loja", "loja@x.com", "SALE", "s", "b", status);
    }

    @Test
    void sendsAndMarksSent() {
        when(repo.find(id)).thenReturn(Optional.of(with(Notification.Status.PENDING)));
        when(guard.tryAcquire(id)).thenReturn(true);

        worker.send(id);

        verify(mailer).send("loja@x.com", "s", "b");
        verify(repo).markSent(id);
    }

    @Test
    void alreadySentIsNeverResent() {
        when(repo.find(id)).thenReturn(Optional.of(with(Notification.Status.SENT)));

        worker.send(id);

        verify(mailer, never()).send(any(), any(), any());
    }

    @Test
    void concurrentDeliveryOfSameJobSendsOnce() {
        when(repo.find(id)).thenReturn(Optional.of(with(Notification.Status.PENDING)));
        when(guard.tryAcquire(id)).thenReturn(false);

        worker.send(id);

        verify(mailer, never()).send(any(), any(), any());
    }

    @Test
    void smtpFailureReleasesGuardAndRethrowsForRetry() {
        when(repo.find(id)).thenReturn(Optional.of(with(Notification.Status.PENDING)));
        when(guard.tryAcquire(id)).thenReturn(true);
        doThrow(new IllegalStateException("smtp fora")).when(mailer).send(any(), any(), any());

        assertThatThrownBy(() -> worker.send(id)).isInstanceOf(IllegalStateException.class);
        verify(guard).release(id);
        verify(repo, never()).markSent(id);
    }
}
