package br.com.marketplace.order.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

record PlaceOrderRequest(
        @NotBlank String buyerId,
        @NotBlank @Email String buyerEmail,
        @NotNull @Valid Address shippingAddress,
        @NotEmpty @Valid List<Item> items) {

    record Item(@NotBlank String sku, @Min(1) @Max(100) int quantity) {}

    record Address(@NotBlank String zipCode, @NotBlank String street, @NotBlank String number,
                   @NotBlank String city, @NotBlank String state) {}
}
