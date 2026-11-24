package br.com.marketplace.catalog.infra;

import br.com.marketplace.catalog.domain.Item;
import br.com.marketplace.catalog.domain.ItemCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache-aside no Redis. Guarda: Item serializado em JSON, chave "catalog:item:{sku}".
 * Por quanto tempo: TTL 10 min + jitter de ate 10% (evita expirar tudo junto).
 * Quando apaga: ao expirar, ou quando o catalogo grava uma versao nova do item (afterCommit).
 * Se o Redis falhar: loga e segue como se fosse miss - o cache e opcional.
 */
@Component
class RedisItemCache implements ItemCache {

    private static final Logger log = LoggerFactory.getLogger(RedisItemCache.class);
    private static final String PREFIX = "catalog:item:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    RedisItemCache(StringRedisTemplate redis, ObjectMapper mapper, @Value("${marketplace.cache.item-ttl:PT10M}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<Item> get(String sku) {
        try {
            String json = redis.opsForValue().get(PREFIX + sku);
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, Item.class));
        } catch (Exception e) {
            log.warn("Cache indisponivel na leitura de {} (seguindo pelo banco): {}", sku, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(Item item) {
        try {
            long jitter = ThreadLocalRandom.current().nextLong(0, ttl.toSeconds() / 10 + 1);
            redis.opsForValue().set(PREFIX + item.sku(), mapper.writeValueAsString(item), ttl.plusSeconds(jitter));
        } catch (Exception e) {
            log.warn("Cache indisponivel na escrita de {}: {}", item.sku(), e.getMessage());
        }
    }

    @Override
    public void evict(String sku) {
        try {
            redis.delete(PREFIX + sku);
        } catch (Exception e) {
            log.warn("Cache indisponivel ao invalidar {} (o TTL cuida): {}", sku, e.getMessage());
        }
    }
}
