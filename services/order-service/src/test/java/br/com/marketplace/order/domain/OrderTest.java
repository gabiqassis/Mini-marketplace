package br.com.marketplace.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    static final ShippingAddress ADDRESS = new ShippingAddress("13010-100", "Rua A", "10", "Campinas", "SP");

    @Test
    void totalIsComputedFromItemsAndPartsAreGroupedBySeller() {
        Order order = Order.place(UUID.randomUUID(), "buyer", "b@x.com", ADDRESS, List.of(
                new OrderItem("A", "loja-1", "A", 2, new BigDecimal("10.00")),
                new OrderItem("B", "loja-2", "B", 1, new BigDecimal("5.50")),
                new OrderItem("C", "loja-1", "C", 1, new BigDecimal("1.25"))));

        assertThat(order.total()).isEqualByComparingTo("26.75");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.itemsBySeller()).containsOnlyKeys("loja-1", "loja-2");
        assertThat(order.amountOf("loja-1")).isEqualByComparingTo("21.25");
        assertThat(order.amountOf("loja-2")).isEqualByComparingTo("5.50");
    }

    @Test
    void emptyOrderIsRefused() {
        assertThatThrownBy(() -> Order.place(UUID.randomUUID(), "b", "b@x.com", ADDRESS, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addressIsValidatedAndNormalized() {
        assertThat(ADDRESS.zipCode()).isEqualTo("13010100");
        assertThatThrownBy(() -> new ShippingAddress("1301", "Rua", "1", "Campinas", "SP")).hasMessageContaining("CEP");
        assertThatThrownBy(() -> new ShippingAddress("13010100", "Rua", "1", "Campinas", "Sao Paulo")).hasMessageContaining("UF");
        assertThatThrownBy(() -> new ShippingAddress("13010100", " ", "1", "Campinas", "SP")).hasMessageContaining("incompleto");
    }
}
