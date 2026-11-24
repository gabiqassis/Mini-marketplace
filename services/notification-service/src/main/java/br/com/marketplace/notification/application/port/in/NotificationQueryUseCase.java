package br.com.marketplace.notification.application.port.in;

import br.com.marketplace.notification.domain.Notification;
import java.util.List;
import java.util.UUID;

/** Porta de entrada: contrato do caso de uso, implementado em NotificationQueryService. */
public interface NotificationQueryUseCase {

    List<Notification> byOrder(UUID orderId);
}
