package br.com.marketplace.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReservationPolicyTest {

    @Test
    void mergesRepeatedSkusAndOrdersThemForStableLocking() {
        Map<String, Integer> merged = ReservationPolicy.merge(List.of(
                new ReservedLine("B", 1), new ReservedLine("A", 2), new ReservedLine("B", 3)));
        assertThat(merged).containsExactly(Map.entry("A", 2), Map.entry("B", 4));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> ReservationPolicy.merge(List.of(new ReservedLine("A", 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsWhenEveryLineFitsTheAvailableStock() {
        var levels = Map.of("A", new StockLevel("A", 10, 8), "B", new StockLevel("B", 1, 0));
        assertThat(ReservationPolicy.rejectionReason(Map.of("A", 2, "B", 1), levels)).isEmpty();
    }

    @Test
    void allOrNothingRejectsTheWholeOrderIfOneLineDoesNotFit() {
        var levels = Map.of("A", new StockLevel("A", 10, 0), "B", new StockLevel("B", 3, 2));
        assertThat(ReservationPolicy.rejectionReason(Map.of("A", 1, "B", 2), levels))
                .contains("INSUFFICIENT_STOCK:B");
    }

    @Test
    void unknownSkuIsRejected() {
        assertThat(ReservationPolicy.rejectionReason(Map.of("Z", 1), Map.of())).contains("UNKNOWN_SKU:Z");
    }

    @Test
    void availableDiscountsWhatIsAlreadyReserved() {
        assertThat(new StockLevel("A", 5, 5).available()).isZero();
    }
}
