package br.com.marketplace.order.infra;

import br.com.marketplace.order.domain.ProductSnapshot;
import br.com.marketplace.order.domain.ProductSnapshotRepository;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProductSnapshotRepository implements ProductSnapshotRepository {

    private final JdbcClient jdbc;

    JdbcProductSnapshotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertIfNewer(ProductSnapshot s) {
        jdbc.sql("""
                        INSERT INTO product_snapshot (sku, seller_id, title, price, sequence)
                        VALUES (:sku, :seller, :title, :price, :seq)
                        ON CONFLICT (sku) DO UPDATE
                           SET title = EXCLUDED.title, price = EXCLUDED.price, sequence = EXCLUDED.sequence, updated_at = now()
                         WHERE product_snapshot.sequence < EXCLUDED.sequence
                           AND product_snapshot.seller_id = EXCLUDED.seller_id
                        """)
                .param("sku", s.sku())
                .param("seller", s.sellerId())
                .param("title", s.title())
                .param("price", s.price())
                .param("seq", s.sequence())
                .update();
    }

    @Override
    public Map<String, ProductSnapshot> findAll(Collection<String> skus) {
        return jdbc.sql("SELECT sku, seller_id, title, price, sequence FROM product_snapshot WHERE sku IN (:skus)")
                .param("skus", skus)
                .query((rs, n) -> new ProductSnapshot(rs.getString("sku"), rs.getString("seller_id"), rs.getString("title"),
                        rs.getBigDecimal("price"), rs.getLong("sequence")))
                .list()
                .stream()
                .collect(Collectors.toMap(ProductSnapshot::sku, Function.identity()));
    }
}
