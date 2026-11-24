package br.com.marketplace.invoice.infra;

import br.com.marketplace.invoice.domain.InvoiceIssuer;
import br.com.marketplace.nfe.NfeEmitterHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * "Gambiarra" controlada para ambiente SEM Lambda (ex.: sem Docker): executa o MESMO handler da
 * Lambda dentro do processo. Ative com marketplace.invoice.issuer=inprocess.
 */
@Component
@ConditionalOnProperty(name = "marketplace.invoice.issuer", havingValue = "inprocess")
class InProcessInvoiceIssuer implements InvoiceIssuer {

    private final NfeEmitterHandler handler;

    InProcessInvoiceIssuer(@Value("${marketplace.invoice.blocked-cnpjs:}") String blocked) {
        this.handler = new NfeEmitterHandler(0, blocked.isBlank() ? Set.of() : Set.of(blocked.split(",")));
    }

    @Override
    public Result issue(Request r) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("orderId", r.orderId().toString());
        input.put("sellerId", r.sellerId());
        input.put("sellerCnpj", r.sellerCnpj());
        input.put("series", r.series());
        input.put("number", r.number());
        input.put("amount", r.amount().toPlainString());
        input.put("issuedAt", r.issuedAt().toString());
        try {
            Map<String, Object> out = handler.handleRequest(input, null);
            return new Result("AUTHORIZED".equals(out.get("status")), (String) out.get("accessKey"), (String) out.get("protocol"),
                    (String) out.get("rejectionCode"), (String) out.get("rejectionReason"));
        } catch (RuntimeException e) {
            throw new TechnicalFailure(e.getMessage(), e);
        }
    }
}
