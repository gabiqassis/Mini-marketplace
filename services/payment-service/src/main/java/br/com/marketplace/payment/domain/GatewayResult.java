package br.com.marketplace.payment.domain;

/**
 * Resposta do gateway em 4 sabores. UNKNOWN e o mais importante: timeout NAO e recusa, o dinheiro
 * pode ter sido autorizado. UNAVAILABLE = a requisicao nem saiu (circuito aberto): seguro recusar.
 */
public record GatewayResult(Outcome outcome, String gatewayRef, String declineCode) {

    public enum Outcome {
        APPROVED,
        DECLINED,
        UNKNOWN,
        UNAVAILABLE
    }

    public static GatewayResult approved(String ref) {
        return new GatewayResult(Outcome.APPROVED, ref, null);
    }

    public static GatewayResult declined(String code) {
        return new GatewayResult(Outcome.DECLINED, null, code);
    }

    public static GatewayResult unknown() {
        return new GatewayResult(Outcome.UNKNOWN, null, null);
    }

    public static GatewayResult unavailable() {
        return new GatewayResult(Outcome.UNAVAILABLE, null, "GATEWAY_UNAVAILABLE");
    }
}
