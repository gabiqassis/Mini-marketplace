package br.com.marketplace.ingestion.domain;

/** Digitos verificadores do CNPJ (modulo 11). Recusa o vendedor ja na entrada. */
public final class CnpjValidator {

    private static final int[] W1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] W2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CnpjValidator() {}

    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 14 || d.chars().distinct().count() == 1) {
            return false;
        }
        return digit(d, W1) == d.charAt(12) - '0' && digit(d, W2) == d.charAt(13) - '0';
    }

    private static int digit(String d, int[] w) {
        int sum = 0;
        for (int i = 0; i < w.length; i++) {
            sum += (d.charAt(i) - '0') * w[i];
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }
}
