package br.com.marketplace.invoice.domain;

import java.util.Optional;

public interface SellerProfileRepository {

    void upsert(SellerFiscalProfile profile);

    Optional<SellerFiscalProfile> find(String sellerId);
}
