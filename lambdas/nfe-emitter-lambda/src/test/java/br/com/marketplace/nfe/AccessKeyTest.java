package br.com.marketplace.nfe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AccessKeyTest {

    private final Cnpj cnpj = Cnpj.of("11222333000181");

    @Test
    void buildsA44DigitKeyWithLayoutAndValidCheckDigit() {
        String key = AccessKey.build("35", YearMonth.of(2026, 11), cnpj, 1, 123, 1, 4567);

        assertThat(key).hasSize(44).containsOnlyDigits();
        assertThat(key).startsWith("352611" + cnpj.digits() + "55" + "001" + "000000123" + "1" + "00004567");
        assertThat(AccessKey.isValid(key)).isTrue();
    }

    @Test
    void detectsTamperedKey() {
        String key = AccessKey.build("35", YearMonth.of(2026, 11), cnpj, 1, 1, 1, 1);
        char last = key.charAt(43);
        String tampered = key.substring(0, 43) + (last == '9' ? '0' : (char) (last + 1));
        assertThat(AccessKey.isValid(tampered)).isFalse();
    }

    @Test
    void checkDigitFollowsModulo11() {
        // 43 zeros e 1: soma = 1*2 = 2 -> 11 - 2 = 9
        assertThat(AccessKey.checkDigit("0".repeat(42) + "1")).isEqualTo(9);
        // tudo zero: resto 0 -> DV 0
        assertThat(AccessKey.checkDigit("0".repeat(43))).isZero();
    }

    @Test
    void refusesNumberOutOfRange() {
        assertThatThrownBy(() -> AccessKey.build("35", YearMonth.of(2026, 1), cnpj, 1, 0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
