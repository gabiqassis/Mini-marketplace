package br.com.marketplace.nfe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NfeEmitterTest {

    private final NfeEmitter emitter = new NfeEmitter();
    private final Instant now = Instant.parse("2026-11-23T15:00:00Z");

    private NfeRequest request(String cnpj, BigDecimal amount) {
        return new NfeRequest("order-1", "seller-1", cnpj, 1, 10, amount, now);
    }

    @Test
    void authorizesValidInvoice() {
        NfeResult result = emitter.authorize(request("11222333000181", new BigDecimal("99.90")));

        assertThat(result.isAuthorized()).isTrue();
        assertThat(AccessKey.isValid(result.accessKey())).isTrue();
        assertThat(result.protocol()).hasSize(15).startsWith("13526");
    }

    @Test
    void sameRequestProducesSameKeySoRetriesDoNotDuplicate() {
        NfeResult first = emitter.authorize(request("11222333000181", BigDecimal.TEN));
        NfeResult second = emitter.authorize(request("11222333000181", BigDecimal.TEN));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void rejectsInvalidIssuerCnpjWithSefazCode207() {
        NfeResult result = emitter.authorize(request("11222333000180", BigDecimal.TEN));
        assertThat(result.isAuthorized()).isFalse();
        assertThat(result.rejectionCode()).isEqualTo("207");
    }

    @Test
    void rejectsZeroAmount() {
        assertThat(emitter.authorize(request("11222333000181", BigDecimal.ZERO)).rejectionCode()).isEqualTo("225");
    }

    @Test
    void handlerMapsJsonInputAndOutput() {
        Map<String, Object> in = new HashMap<>();
        in.put("orderId", "order-1");
        in.put("sellerId", "seller-1");
        in.put("sellerCnpj", "11.222.333/0001-81");
        in.put("series", 1);
        in.put("number", 7);
        in.put("amount", "150.00");
        in.put("issuedAt", now.toString());

        Map<String, Object> out = new NfeEmitterHandler(0).handleRequest(in, null);

        assertThat(out.get("status")).isEqualTo("AUTHORIZED");
        assertThat((String) out.get("accessKey")).hasSize(44);
    }

    @Test
    void handlerSimulatesSefazOutageAsTechnicalError() {
        assertThatThrownBy(() -> new NfeEmitterHandler(1.0).handleRequest(new HashMap<>(), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blockedIssuerIsRejectedWithCode203() {
        NfeResult result = new NfeEmitter(java.util.Set.of("11.444.777/0001-61"))
                .authorize(new NfeRequest("o", "s", "11444777000161", 1, 1, BigDecimal.TEN, now));
        assertThat(result.rejectionCode()).isEqualTo("203");
    }
}
