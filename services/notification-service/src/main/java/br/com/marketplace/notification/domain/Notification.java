package br.com.marketplace.notification.domain;

import java.util.UUID;

public record Notification(
        UUID id, UUID orderId, Draft.Recipient recipient, String recipientId, String email, String template,
        String subject, String body, Status status) {

    public enum Status {
        PENDING,
        SENT,
        FAILED
    }
}
