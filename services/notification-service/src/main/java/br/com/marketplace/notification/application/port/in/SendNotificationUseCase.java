package br.com.marketplace.notification.application.port.in;

import java.util.UUID;

/** Porta de entrada: contrato do caso de uso, implementado em SendNotificationWorker. */
public interface SendNotificationUseCase {

    void send(UUID notificationId);

    void giveUp(UUID notificationId, String reason);
}
