package br.com.marketplace.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchValidatorTest {

    private final BatchValidator validator = new BatchValidator();
    private final SellerBatch.SellerProfile seller = new SellerBatch.SellerProfile("Loja X LTDA", "11.222.333/0001-81", "loja@x.com");

    private static SellerBatch.Item item(String sku, String price, Integer qty, Long seq) {
        return new SellerBatch.Item(sku, "Produto " + sku, price == null ? null : new BigDecimal(price), qty, seq);
    }

    private SellerBatch batch(List<SellerBatch.Item> items) {
        return new SellerBatch("b-1", "loja-x", seller, items);
    }

    @Test
    void validHeaderPasses() {
        validator.validateHeader(batch(List.of(item("SKU-1", "10.00", 5, 1L))));
    }

    @Test
    void invalidCnpjRejectsWholeBatch() {
        SellerBatch b = new SellerBatch("b-1", "loja-x",
                new SellerBatch.SellerProfile("Loja", "11.222.333/0001-80", "a@b.com"), List.of(item("SKU-1", "1", 1, 1L)));
        assertThatThrownBy(() -> validator.validateHeader(b)).isInstanceOf(InvalidBatchException.class).hasMessageContaining("CNPJ");
    }

    @Test
    void badSellerIdOrEmptyItemsRejectsWholeBatch() {
        assertThatThrownBy(() -> validator.validateHeader(new SellerBatch("b", "Loja X!", seller, List.of(item("SKU-1", "1", 1, 1L)))))
                .isInstanceOf(InvalidBatchException.class);
        assertThatThrownBy(() -> validator.validateHeader(batch(List.of()))).isInstanceOf(InvalidBatchException.class);
        assertThatThrownBy(() -> validator.validateHeader(null)).isInstanceOf(InvalidBatchException.class);
    }

    @Test
    void tooManyItemsRejectsWholeBatch() {
        List<SellerBatch.Item> many = new ArrayList<>();
        for (int i = 0; i <= BatchValidator.MAX_ITEMS; i++) {
            many.add(item("SKU-" + i, "1", 1, 1L));
        }
        assertThatThrownBy(() -> validator.validateHeader(batch(many))).isInstanceOf(InvalidBatchException.class);
    }

    @Test
    void invalidItemsAreRejectedIndividuallyWithReason() {
        List<ItemVerdict> verdicts = validator.validateItems(batch(List.of(
                item("SKU-OK", "10.00", 5, 1L),
                item("sku minusculo", "10.00", 5, 1L),
                item("SKU-NEG", "-1", 5, 1L),
                item("SKU-3DEC", "1.999", 5, 1L),
                item("SKU-STOCK", "10", -1, 1L),
                item("SKU-SEQ", "10", 1, 0L),
                item("SKU-OK", "11.00", 3, 2L))));

        assertThat(verdicts).extracting(ItemVerdict::accepted)
                .containsExactly(true, false, false, false, false, false, false);
        assertThat(verdicts.get(1).reason()).contains("sku");
        assertThat(verdicts.get(2).reason()).contains("preco");
        assertThat(verdicts.get(3).reason()).contains("2 casas");
        assertThat(verdicts.get(4).reason()).contains("estoque");
        assertThat(verdicts.get(5).reason()).contains("sequence");
        assertThat(verdicts.get(6).reason()).contains("repetido");
    }

    @Test
    void zeroStockIsValidSoldOutItem() {
        assertThat(validator.validateItems(batch(List.of(item("SKU-ZERO", "5", 0, 1L)))).getFirst().accepted()).isTrue();
    }
}
