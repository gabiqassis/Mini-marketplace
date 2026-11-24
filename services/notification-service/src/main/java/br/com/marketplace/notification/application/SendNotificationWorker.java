package br.com.marketplace.notification.application;

import br.com.marketplace.notification.domain.Mailer;
import br.com.marketplace.notification.domain.Notification;
import br.com.marketplace.notification.domain.NotificationRepository;
import br.com.marketplace.notification.domain.SendGuard;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Worker da fila notification.send. Envio de e-mail e efeito EXTERNO (nao volta atras com rollback),
 * entao a idempotencia tem duas camadas: status no banco (SENT nao reenvia) e trava no Redis
 * (dois workers / reentrega nao enviam o mesmo e-mail ao mesmo tempo).
 */
@Service
public class SendNotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(SendNotificationWorker.class);

    private final NotificationRepository notifications;
    private final Mailer mailer;
    private final SendGuard guard;

    public SendNotificationWorker(NotificationRepository notifications, Mailer mailer, SendGuard guard) {
        this.notifications = notifications;
        this.mailer = mailer;
        this.guard = guard;
    }

    public void send(UUID notificationId) {
        Notification n = notifications.find(notificationId).orElse(null);
        if (n == null || n.status() != Notification.Status.PENDING) {
            return;
        }
        if (!guard.tryAcquire(notificationId)) {
            log.info("Notificacao {} ja esta sendo enviada por outro worker", notificationId);
            return;
        }
        try {
            mailer.send(n.email(), n.subject(), n.body());
        } catch (RuntimeException e) {
            guard.release(notificationId);
            throw e; // RabbitMQ faz retry com backoff
        }
        notifications.markSent(notificationId);
        log.info("Notificacao {} ({}) enviada para {}", notificationId, n.template(), n.recipient());
    }

    public void giveUp(UUID notificationId, String reason) {
        notifications.markFailed(notificationId, reason);
    }
}
