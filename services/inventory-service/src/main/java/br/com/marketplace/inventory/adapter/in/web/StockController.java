package br.com.marketplace.inventory.adapter.in.web;

import br.com.marketplace.inventory.application.port.in.StockQueryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stock")
class StockController {

    record StockView(String sku, int onHand, int reserved, int available) {}

    private final StockQueryUseCase service;

    StockController(StockQueryUseCase service) {
        this.service = service;
    }

    @GetMapping("/{sku}")
    ResponseEntity<StockView> get(@PathVariable String sku) {
        return service.find(sku)
                .map(s -> ResponseEntity.ok(new StockView(s.sku(), s.onHand(), s.reserved(), s.available())))
                .orElse(ResponseEntity.notFound().build());
    }
}
