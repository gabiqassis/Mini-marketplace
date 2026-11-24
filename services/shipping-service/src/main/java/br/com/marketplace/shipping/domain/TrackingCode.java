package br.com.marketplace.shipping.domain;

import java.util.function.LongSupplier;

/** Codigo de rastreio no formato dos Correios: 2 letras + 9 digitos + BR. */
public final class TrackingCode {

    private TrackingCode() {}

    public static String next(LongSupplier random) {
        long digits = Math.floorMod(random.getAsLong(), 1_000_000_000L);
        return "MM" + String.format("%09d", digits) + "BR";
    }

    public static boolean isValid(String code) {
        return code != null && code.matches("[A-Z]{2}\\d{9}BR");
    }
}
