package br.com.marketplace.shipping.config;

import br.com.marketplace.shipping.adapter.in.web.WebhookSignature;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ShippingConfig {

    /** Segredo vem de variavel de ambiente (em producao: Secrets Manager/Vault), nunca do codigo. */
    @Bean
    WebhookSignature webhookSignature(
            @Value("${marketplace.carrier.webhook-secret}") String secret,
            @Value("${marketplace.carrier.webhook-tolerance:PT5M}") Duration tolerance) {
        return new WebhookSignature(secret, tolerance);
    }
}
