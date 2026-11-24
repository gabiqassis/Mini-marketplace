package br.com.marketplace.invoice.application.port.out;

import br.com.marketplace.invoice.domain.SellerFiscalProfile;
import java.util.Optional;

public interface SellerProfileRepository {

    void upsert(SellerFiscalProfile profile);

    Optional<SellerFiscalProfile> find(String sellerId);
}
