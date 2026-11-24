package br.com.marketplace.shipping.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Cache do rastreio (consulta mais frequente do comprador). Falha do cache nunca quebra a leitura. */
public interface TrackingCache {

    Optional<String> get(UUID orderId);

    void put(UUID orderId, String json);

    void evict(UUID orderId);
}
