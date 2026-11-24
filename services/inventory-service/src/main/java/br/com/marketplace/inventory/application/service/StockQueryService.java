package br.com.marketplace.inventory.application.service;

import br.com.marketplace.inventory.application.port.in.StockQueryUseCase;
import br.com.marketplace.inventory.domain.StockLevel;
import br.com.marketplace.inventory.application.port.out.StockRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class StockQueryService implements StockQueryUseCase {

    private final StockRepository stock;

    public StockQueryService(StockRepository stock) {
        this.stock = stock;
    }

    @Override
    public Optional<StockLevel> find(String sku) {
        return stock.find(sku);
    }
}
