package br.com.marketplace.inventory.application.port.in;

import br.com.marketplace.inventory.domain.StockLevel;
import java.util.Optional;

/** Porta de entrada: contrato do caso de uso, implementado em StockQueryService. */
public interface StockQueryUseCase {

    Optional<StockLevel> find(String sku);
}
