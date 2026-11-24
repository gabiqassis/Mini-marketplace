package br.com.marketplace.catalog.adapter.in.web;

import br.com.marketplace.catalog.application.port.in.ItemQueryUseCase;
import br.com.marketplace.catalog.domain.Item;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/items")
class ItemController {

    private final ItemQueryUseCase service;

    ItemController(ItemQueryUseCase service) {
        this.service = service;
    }

    @GetMapping("/{sku}")
    ResponseEntity<Item> get(@PathVariable String sku) {
        return service.findBySku(sku).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
