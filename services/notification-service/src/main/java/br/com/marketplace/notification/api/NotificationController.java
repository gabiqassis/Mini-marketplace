package br.com.marketplace.notification.api;

import br.com.marketplace.notification.application.NotificationQueryService;
import br.com.marketplace.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
class NotificationController {

    private final NotificationQueryService query;

    NotificationController(NotificationQueryService query) {
        this.query = query;
    }

    @GetMapping
    List<Notification> byOrder(@RequestParam UUID orderId) {
        return query.byOrder(orderId);
    }
}
