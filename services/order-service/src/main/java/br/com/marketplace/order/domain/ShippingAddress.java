package br.com.marketplace.order.domain;

import java.util.regex.Pattern;

/** Endereco de entrega validado ja na entrada (endereco ruim vira envio devolvido depois). */
public record ShippingAddress(String zipCode, String street, String number, String city, String state) {

    private static final Pattern CEP = Pattern.compile("\\d{5}-?\\d{3}");
    private static final Pattern UF = Pattern.compile("[A-Z]{2}");

    public ShippingAddress {
        if (zipCode == null || !CEP.matcher(zipCode).matches()) {
            throw new IllegalArgumentException("CEP invalido");
        }
        if (blank(street) || blank(number) || blank(city)) {
            throw new IllegalArgumentException("endereco incompleto");
        }
        if (state == null || !UF.matcher(state).matches()) {
            throw new IllegalArgumentException("UF invalida");
        }
        zipCode = zipCode.replace("-", "");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
