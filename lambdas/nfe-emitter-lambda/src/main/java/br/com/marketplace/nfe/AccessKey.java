package br.com.marketplace.nfe;

import java.time.YearMonth;

/**
 * Chave de acesso da NF-e (44 digitos):
 * cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) DV(1).
 * DV = modulo 11 com pesos 2..9 da direita para a esquerda.
 */
public final class AccessKey {

    public static final String UF_SP = "35";
    public static final String MODEL_NFE = "55";

    private AccessKey() {}

    public static String build(String uf, YearMonth issued, Cnpj cnpj, int series, long number, int emissionType, int randomCode) {
        if (number < 1 || number > 999_999_999L) {
            throw new IllegalArgumentException("numero da nota fora da faixa");
        }
        String base = uf
                + String.format("%02d%02d", issued.getYear() % 100, issued.getMonthValue())
                + cnpj.digits()
                + MODEL_NFE
                + String.format("%03d", series)
                + String.format("%09d", number)
                + emissionType
                + String.format("%08d", randomCode);
        return base + checkDigit(base);
    }

    public static int checkDigit(String first43) {
        if (first43.length() != 43) {
            throw new IllegalArgumentException("esperado 43 digitos");
        }
        int sum = 0;
        int weight = 2;
        for (int i = first43.length() - 1; i >= 0; i--) {
            sum += (first43.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    public static boolean isValid(String key) {
        return key != null
                && key.matches("\\d{44}")
                && checkDigit(key.substring(0, 43)) == key.charAt(43) - '0';
    }
}
