package br.com.marketplace.nfe;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * Regra pura de autorizacao (simula a SEFAZ). Deterministica: o MESMO pedido gera a MESMA chave e o
 * MESMO protocolo, entao reenviar a requisicao (retry) nao cria nota duplicada.
 *
 * <p>Rejeicoes simuladas (codigos reais da SEFAZ):
 * 207 = CNPJ do emitente invalido; 203 = emissor nao habilitado; 225 = falha no schema.
 */
public final class NfeEmitter {

    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    private final Set<String> blockedCnpjs;

    public NfeEmitter() {
        this(Set.of());
    }

    /** @param blockedCnpjs emitentes nao habilitados na "SEFAZ" (cadastro irregular). */
    public NfeEmitter(Set<String> blockedCnpjs) {
        this.blockedCnpjs = blockedCnpjs.stream().map(c -> c.replaceAll("\\D", "")).collect(java.util.stream.Collectors.toSet());
    }

    public NfeResult authorize(NfeRequest request) {
        if (request.sellerCnpj() == null || !Cnpj.isValid(request.sellerCnpj())) {
            return NfeResult.rejected("207", "Rejeicao: CNPJ do emitente invalido");
        }
        if (blockedCnpjs.contains(request.sellerCnpj().replaceAll("\\D", ""))) {
            return NfeResult.rejected("203", "Rejeicao: emissor nao habilitado para emissao da NF-e");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return NfeResult.rejected("225", "Rejeicao: valor total da nota invalido");
        }
        if (request.number() < 1 || request.issuedAt() == null || request.orderId() == null) {
            return NfeResult.rejected("225", "Rejeicao: falha no schema XML");
        }
        Cnpj cnpj = Cnpj.of(request.sellerCnpj());
        int randomCode = (int) (Math.abs(stableHash(request.orderId() + cnpj.digits())) % 100_000_000L);
        YearMonth issued = YearMonth.from(request.issuedAt().atZone(BRAZIL));
        String key = AccessKey.build(AccessKey.UF_SP, issued, cnpj, request.series(), request.number(), 1, randomCode);
        String protocol = "135" + String.format("%02d", issued.getYear() % 100)
                + String.format("%010d", Math.abs(stableHash(key)) % 10_000_000_000L);
        return NfeResult.authorized(key, protocol);
    }

    private static long stableHash(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
    }
}
