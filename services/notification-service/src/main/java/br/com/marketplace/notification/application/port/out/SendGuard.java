package br.com.marketplace.notification.application.port.out;

import java.util.UUID;

/**
 * Trava curta contra envio DUPLICADO do mesmo e-mail (o job pode ser reentregue se o worker cair
 * depois de enviar e antes de gravar SENT). Implementada com SET NX + TTL no Redis.
 */
public interface SendGuard {

    /** @return true se este worker pode enviar agora. */
    boolean tryAcquire(UUID notificationId);

    /** Libera a trava quando o envio falhou (para o retry poder enviar). */
    void release(UUID notificationId);
}
