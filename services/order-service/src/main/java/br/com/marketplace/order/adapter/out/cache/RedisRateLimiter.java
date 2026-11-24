package br.com.marketplace.order.adapter.out.cache;

import br.com.marketplace.order.application.port.out.RateLimiter;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Janela fixa no Redis: INCR na chave do minuto atual + EXPIRE. Protege o checkout contra rajada
 * (bot, botao clicado 50 vezes). Redis fora do ar = deixa passar (fail-open): limitar e otimizacao,
 * vender e o negocio.
 */
@Component
class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final StringRedisTemplate redis;
    private final int limitPerMinute;

    RedisRateLimiter(StringRedisTemplate redis, @Value("${marketplace.rate-limit.orders-per-minute:30}") int limitPerMinute) {
        this.redis = redis;
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    public boolean tryAcquire(String key) {
        String bucket = "ratelimit:" + key + ":" + Instant.now().getEpochSecond() / 60;
        try {
            Long count = redis.opsForValue().increment(bucket);
            if (count != null && count == 1L) {
                redis.expire(bucket, Duration.ofSeconds(70));
            }
            return count == null || count <= limitPerMinute;
        } catch (RuntimeException e) {
            log.warn("Rate limiter indisponivel, liberando requisicao: {}", e.getMessage());
            return true;
        }
    }
}
