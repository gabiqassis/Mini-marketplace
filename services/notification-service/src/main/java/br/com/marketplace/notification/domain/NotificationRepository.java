package br.com.marketplace.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    /** UNIQUE(dedupe_key): o mesmo fato nunca gera o mesmo aviso duas vezes. @return false se ja existia. */
    boolean insertIfAbsent(Notification notification, String dedupeKey);

    Optional<Notification> find(UUID id);

    void markSent(UUID id);

    void markFailed(UUID id, String reason);

    List<Notification> findByOrder(UUID orderId);

    void saveSellerEmail(String sellerId, String email);

    Optional<String> sellerEmail(String sellerId);

    void saveBuyerEmail(UUID orderId, String buyerId, String email);

    Optional<String> buyerEmail(UUID orderId);

    Optional<String> buyerId(UUID orderId);
}
