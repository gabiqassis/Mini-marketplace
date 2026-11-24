package br.com.marketplace.notification.application.service;

import br.com.marketplace.notification.application.port.in.NotificationQueryUseCase;
import br.com.marketplace.notification.domain.Notification;
import br.com.marketplace.notification.application.port.out.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationQueryService implements NotificationQueryUseCase {

    private final NotificationRepository notifications;

    public NotificationQueryService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public List<Notification> byOrder(UUID orderId) {
        return notifications.findByOrder(orderId);
    }
}
