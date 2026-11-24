package br.com.marketplace.shipping.application;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verificacao de webhook (boas praticas OWASP / gateways de pagamento):
 * <ul>
 *   <li>HMAC-SHA256 sobre "timestamp.corpoBruto" com segredo compartilhado.</li>
 *   <li>Corpo BRUTO: reformatar o JSON antes muda a assinatura.</li>
 *   <li>Timestamp com tolerancia (anti-replay): requisicao antiga capturada nao vale.</li>
 *   <li>Comparacao em tempo constante (MessageDigest.isEqual): nao vaza a assinatura por tempo.</li>
 * </ul>
 */
public final class WebhookSignature {

    private final byte[] secret;
    private final Duration tolerance;

    public WebhookSignature(String secret, Duration tolerance) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("segredo do webhook deve ter ao menos 16 caracteres");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tolerance = tolerance;
    }

    public String sign(String timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isValid(String timestamp, String rawBody, String signature, Instant now) {
        if (timestamp == null || signature == null || rawBody == null) {
            return false;
        }
        Instant sentAt;
        try {
            sentAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (NumberFormatException e) {
            return false;
        }
        if (Duration.between(sentAt, now).abs().compareTo(tolerance) > 0) {
            return false;
        }
        byte[] expected = sign(timestamp, rawBody).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
