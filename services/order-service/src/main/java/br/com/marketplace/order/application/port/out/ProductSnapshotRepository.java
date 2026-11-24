package br.com.marketplace.order.application.port.out;

import br.com.marketplace.order.domain.ProductSnapshot;
import java.util.Collection;
import java.util.Map;

/** Porta de saida: copia local de preco/vendedor. */
public interface ProductSnapshotRepository {

    void upsertIfNewer(ProductSnapshot snapshot);

    Map<String, ProductSnapshot> findAll(Collection<String> skus);
}
