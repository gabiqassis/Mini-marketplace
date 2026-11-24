package br.com.marketplace.notification.adapter.out.cache;

import br.com.marketplace.notification.application.port.out.SendGuard;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** SET NX com TTL de 10 min. Redis fora: libera (o status SENT no banco ainda evita a maioria dos duplicados). */
@Component
class RedisSendGuard implements SendGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisSendGuard.class);
    private static final String PREFIX = "notification:sending:";

    private final StringRedisTemplate redis;

    RedisSendGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(UUID id) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + id, "1", Duration.ofMinutes(10)));
        } catch (RuntimeException e) {
            log.warn("Redis indisponivel na trava de envio: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void release(UUID id) {
        try {
            redis.delete(PREFIX + id);
        } catch (RuntimeException e) {
            log.warn("Redis indisponivel ao liberar trava (TTL cuida): {}", e.getMessage());
        }
    }
}
