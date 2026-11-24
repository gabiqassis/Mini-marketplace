package br.com.marketplace.inventory.application;

import br.com.marketplace.inventory.domain.StockLevel;
import br.com.marketplace.inventory.domain.StockRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class StockQueryService {

    private final StockRepository stock;

    public StockQueryService(StockRepository stock) {
        this.stock = stock;
    }

    public Optional<StockLevel> find(String sku) {
        return stock.find(sku);
    }
}
