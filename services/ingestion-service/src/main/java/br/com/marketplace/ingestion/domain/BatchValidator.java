package br.com.marketplace.ingestion.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Entrada externa nunca e confiavel. Duas camadas:
 * cabecalho invalido derruba o lote inteiro; item invalido e rejeitado sozinho (o resto segue).
 */
public final class BatchValidator {

    public static final int MAX_ITEMS = 500;
    private static final Pattern SELLER_ID = Pattern.compile("[a-z0-9][a-z0-9-]{2,63}");
    private static final Pattern SKU = Pattern.compile("[A-Z0-9][A-Z0-9-]{2,63}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000.00");

    public void validateHeader(SellerBatch batch) {
        if (batch == null) {
            throw new InvalidBatchException("lote vazio");
        }
        require(batch.batchId() != null && !batch.batchId().isBlank() && batch.batchId().length() <= 64,
                "batchId obrigatorio (ate 64 caracteres)");
        require(batch.sellerId() != null && SELLER_ID.matcher(batch.sellerId()).matches(),
                "sellerId invalido (minusculas, numeros e hifen, 3 a 64)");
        require(batch.seller() != null, "dados do vendedor obrigatorios");
        require(batch.seller().legalName() != null && !batch.seller().legalName().isBlank(), "razao social obrigatoria");
        require(CnpjValidator.isValid(batch.seller().cnpj()), "CNPJ do vendedor invalido");
        require(batch.seller().email() != null && EMAIL.matcher(batch.seller().email()).matches(), "e-mail do vendedor invalido");
        require(batch.items() != null && !batch.items().isEmpty(), "lote sem itens");
        require(batch.items().size() <= MAX_ITEMS, "lote acima de " + MAX_ITEMS + " itens");
    }

    public List<ItemVerdict> validateItems(SellerBatch batch) {
        List<ItemVerdict> verdicts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SellerBatch.Item item : batch.items()) {
            String problem = problemOf(item);
            if (problem == null && !seen.add(item.sku())) {
                problem = "sku repetido no mesmo lote";
            }
            verdicts.add(problem == null ? ItemVerdict.accept(item) : ItemVerdict.reject(item, problem));
        }
        return verdicts;
    }

    private static String problemOf(SellerBatch.Item item) {
        if (item == null) {
            return "item nulo";
        }
        if (item.sku() == null || !SKU.matcher(item.sku()).matches()) {
            return "sku invalido (maiusculas, numeros e hifen, 3 a 64)";
        }
        if (item.title() == null || item.title().isBlank() || item.title().length() > 200) {
            return "titulo obrigatorio (ate 200 caracteres)";
        }
        if (item.price() == null || item.price().signum() <= 0 || item.price().compareTo(MAX_PRICE) > 0) {
            return "preco deve ser maior que zero e ate 1.000.000";
        }
        if (item.price().scale() > 2) {
            return "preco com mais de 2 casas decimais";
        }
        if (item.stockQty() == null || item.stockQty() < 0 || item.stockQty() > 1_000_000) {
            return "estoque deve estar entre 0 e 1.000.000";
        }
        if (item.sequence() == null || item.sequence() < 1) {
            return "sequence obrigatoria e positiva (ordem das atualizacoes)";
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidBatchException(message);
        }
    }
}
