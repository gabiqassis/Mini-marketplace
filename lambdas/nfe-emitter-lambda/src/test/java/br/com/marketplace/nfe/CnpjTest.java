package br.com.marketplace.nfe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class CnpjTest {

    @ParameterizedTest
    @ValueSource(strings = {"11222333000181", "11.222.333/0001-81", "11444777000161"})
    void acceptsValidCnpjWithOrWithoutMask(String raw) {
        assertThat(Cnpj.isValid(raw)).isTrue();
        assertThat(Cnpj.of(raw).digits()).hasSize(14).containsOnlyDigits();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11222333000180", "00000000000000", "123", "", "1122233300018a"})
    void rejectsInvalidCnpj(String raw) {
        assertThat(Cnpj.isValid(raw)).isFalse();
    }

    @Test
    void constructorRefusesInvalidValue() {
        assertThatThrownBy(() -> Cnpj.of("11222333000180")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Cnpj.isValid(null)).isFalse();
    }
}
