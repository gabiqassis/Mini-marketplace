package br.com.marketplace.nfe;

/** CNPJ com digitos verificadores validados (modulo 11). Aceita com ou sem mascara. */
public record Cnpj(String digits) {

    private static final int[] W1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] W2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public Cnpj {
        if (!isValid(digits)) {
            throw new IllegalArgumentException("CNPJ invalido");
        }
        digits = onlyDigits(digits);
    }

    public static Cnpj of(String raw) {
        return new Cnpj(raw);
    }

    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String d = onlyDigits(raw);
        if (d.length() != 14 || d.chars().distinct().count() == 1) {
            return false;
        }
        int dv1 = checkDigit(d, W1);
        int dv2 = checkDigit(d, W2);
        return dv1 == d.charAt(12) - '0' && dv2 == d.charAt(13) - '0';
    }

    private static int checkDigit(String d, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (d.charAt(i) - '0') * weights[i];
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    private static String onlyDigits(String raw) {
        return raw.replaceAll("\\D", "");
    }
}
