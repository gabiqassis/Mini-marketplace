package br.com.marketplace.shipping.adapter.out.cache;

import br.com.marketplace.shipping.application.port.out.TrackingCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Guarda: JSON do rastreio do pedido, chave "shipping:tracking:{orderId}".
 * TTL: 60 s (rastreio muda pouco, mas e consultado muito). Apaga: a cada mudanca de status (afterCommit).
 * Redis fora: segue pelo banco.
 */
@Component
class RedisTrackingCache implements TrackingCache {

    private static final Logger log = LoggerFactory.getLogger(RedisTrackingCache.class);
    private static final String PREFIX = "shipping:tracking:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    RedisTrackingCache(StringRedisTemplate redis, @Value("${marketplace.cache.tracking-ttl:PT60S}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public Optional<String> get(UUID orderId) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + orderId));
        } catch (RuntimeException e) {
            log.warn("Cache de rastreio indisponivel: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID orderId, String json) {
        try {
            redis.opsForValue().set(PREFIX + orderId, json, ttl);
        } catch (RuntimeException e) {
            log.warn("Cache de rastreio indisponivel: {}", e.getMessage());
        }
    }

    @Override
    public void evict(UUID orderId) {
        try {
            redis.delete(PREFIX + orderId);
        } catch (RuntimeException e) {
            log.warn("Cache de rastreio indisponivel ao invalidar (TTL cuida): {}", e.getMessage());
        }
    }
}
