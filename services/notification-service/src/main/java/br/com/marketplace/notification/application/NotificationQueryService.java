package br.com.marketplace.notification.application;

import br.com.marketplace.notification.domain.Notification;
import br.com.marketplace.notification.domain.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationQueryService {

    private final NotificationRepository notifications;

    public NotificationQueryService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public List<Notification> byOrder(UUID orderId) {
        return notifications.findByOrder(orderId);
    }
}
