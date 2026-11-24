package br.com.marketplace.catalog.infra;

import br.com.marketplace.catalog.domain.Item;
import br.com.marketplace.catalog.domain.ItemRepository;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcItemRepository implements ItemRepository {

    private final JdbcClient jdbc;

    JdbcItemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean upsertIfNewer(Item item) {
        // WHERE do DO UPDATE e a trava: so avanca sequence, e so o dono do sku altera.
        return jdbc.sql("""
                        INSERT INTO items (sku, seller_id, title, price, sequence)
                        VALUES (:sku, :sellerId, :title, :price, :sequence)
                        ON CONFLICT (sku) DO UPDATE
                           SET title = EXCLUDED.title,
                               price = EXCLUDED.price,
                               sequence = EXCLUDED.sequence,
                               updated_at = now()
                         WHERE items.sequence < EXCLUDED.sequence
                           AND items.seller_id = EXCLUDED.seller_id
                        """)
                        .param("sku", item.sku())
                        .param("sellerId", item.sellerId())
                        .param("title", item.title())
                        .param("price", item.price())
                        .param("sequence", item.sequence())
                        .update()
                == 1;
    }

    @Override
    public Optional<Item> findBySku(String sku) {
        return jdbc.sql("SELECT sku, seller_id, title, price, sequence FROM items WHERE sku = :sku")
                .param("sku", sku)
                .query((rs, n) -> new Item(
                        rs.getString("sku"),
                        rs.getString("seller_id"),
                        rs.getString("title"),
                        rs.getBigDecimal("price"),
                        rs.getLong("sequence")))
                .optional();
    }
}
