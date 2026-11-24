package br.com.marketplace.catalog.application.port.in;

import br.com.marketplace.catalog.domain.Item;
import java.util.Optional;

/** Porta de entrada: contrato do caso de uso, implementado em ItemQueryService. */
public interface ItemQueryUseCase {

    Optional<Item> findBySku(String sku);
}
