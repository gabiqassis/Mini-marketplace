package br.com.marketplace.nfe;

/** Resposta da "SEFAZ": AUTHORIZED com chave e protocolo, ou REJECTED com codigo de rejeicao. */
public record NfeResult(String status, String accessKey, String protocol, String rejectionCode, String rejectionReason) {

    public static final String AUTHORIZED = "AUTHORIZED";
    public static final String REJECTED = "REJECTED";

    public static NfeResult authorized(String accessKey, String protocol) {
        return new NfeResult(AUTHORIZED, accessKey, protocol, null, null);
    }

    public static NfeResult rejected(String code, String reason) {
        return new NfeResult(REJECTED, null, null, code, reason);
    }

    public boolean isAuthorized() {
        return AUTHORIZED.equals(status);
    }
}
