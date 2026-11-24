package br.com.marketplace.nfe;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Entrada da Lambda. Usa Map (JSON generico) para nao depender de serializacao customizada.
 *
 * <p>Variavel SEFAZ_FAILURE_RATE (0..1) simula instabilidade da SEFAZ: a Lambda lanca erro e o
 * chamador trata como falha TECNICA (retry), diferente de rejeicao FISCAL (nao adianta repetir).
 */
public class NfeEmitterHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final NfeEmitter emitter;
    private final double failureRate;

    public NfeEmitterHandler() {
        this(parseRate(System.getenv("SEFAZ_FAILURE_RATE")), parseList(System.getenv("SEFAZ_BLOCKED_CNPJS")));
    }

    public NfeEmitterHandler(double failureRate, java.util.Set<String> blockedCnpjs) {
        this.failureRate = failureRate;
        this.emitter = new NfeEmitter(blockedCnpjs);
    }

    NfeEmitterHandler(double failureRate) {
        this(failureRate, java.util.Set.of());
    }

    private static java.util.Set<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Set.of();
        }
        return java.util.Set.of(raw.split(","));
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new IllegalStateException("SEFAZ indisponivel (simulado)");
        }
        NfeRequest request = new NfeRequest(
                str(input, "orderId"),
                str(input, "sellerId"),
                str(input, "sellerCnpj"),
                (int) num(input, "series", 1),
                (long) num(input, "number", 0),
                input.get("amount") == null ? null : new BigDecimal(String.valueOf(input.get("amount"))),
                input.get("issuedAt") == null ? null : Instant.parse(String.valueOf(input.get("issuedAt"))));
        NfeResult result = emitter.authorize(request);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", result.status());
        out.put("accessKey", result.accessKey());
        out.put("protocol", result.protocol());
        out.put("rejectionCode", result.rejectionCode());
        out.put("rejectionReason", result.rejectionReason());
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("NF-e pedido=" + request.orderId() + " status=" + result.status());
        }
        return out;
    }

    private static String str(Map<String, Object> in, String key) {
        Object v = in.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static double num(Map<String, Object> in, String key, double fallback) {
        Object v = in.get(key);
        return v == null ? fallback : Double.parseDouble(String.valueOf(v));
    }

    private static double parseRate(String raw) {
        try {
            return raw == null ? 0 : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
