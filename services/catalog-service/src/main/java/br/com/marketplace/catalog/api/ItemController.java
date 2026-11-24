package br.com.marketplace.catalog.api;

import br.com.marketplace.catalog.application.ItemQueryService;
import br.com.marketplace.catalog.domain.Item;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/items")
class ItemController {

    private final ItemQueryService service;

    ItemController(ItemQueryService service) {
        this.service = service;
    }

    @GetMapping("/{sku}")
    ResponseEntity<Item> get(@PathVariable String sku) {
        return service.findBySku(sku).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
