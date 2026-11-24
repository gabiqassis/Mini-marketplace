package br.com.marketplace.inventory.infra;

import br.com.marketplace.inventory.domain.StockLevel;
import br.com.marketplace.inventory.domain.StockRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStockRepository implements StockRepository {

    private final JdbcClient jdbc;

    JdbcStockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void applySellerStock(String sku, int quantity, long sequence) {
        jdbc.sql("""
                        INSERT INTO stock (sku, on_hand, reserved, sequence)
                        VALUES (:sku, :qty, 0, :seq)
                        ON CONFLICT (sku) DO UPDATE
                           SET on_hand = GREATEST(EXCLUDED.on_hand, stock.reserved),
                               sequence = EXCLUDED.sequence,
                               updated_at = now()
                         WHERE stock.sequence < EXCLUDED.sequence
                        """)
                .param("sku", sku)
                .param("qty", quantity)
                .param("seq", sequence)
                .update();
    }

    @Override
    public List<StockLevel> lockLevels(Collection<String> skus) {
        return jdbc.sql("SELECT sku, on_hand, reserved FROM stock WHERE sku IN (:skus) ORDER BY sku FOR UPDATE")
                .param("skus", skus)
                .query((rs, n) -> new StockLevel(rs.getString("sku"), rs.getInt("on_hand"), rs.getInt("reserved")))
                .list();
    }

    @Override
    public void addReserved(String sku, int delta) {
        jdbc.sql("UPDATE stock SET reserved = reserved + :d, updated_at = now() WHERE sku = :sku")
                .param("d", delta)
                .param("sku", sku)
                .update();
    }

    @Override
    public void commit(String sku, int quantity) {
        jdbc.sql("UPDATE stock SET on_hand = on_hand - :q, reserved = reserved - :q, updated_at = now() WHERE sku = :sku")
                .param("q", quantity)
                .param("sku", sku)
                .update();
    }

    @Override
    public Optional<StockLevel> find(String sku) {
        return jdbc.sql("SELECT sku, on_hand, reserved FROM stock WHERE sku = :sku")
                .param("sku", sku)
                .query((rs, n) -> new StockLevel(rs.getString("sku"), rs.getInt("on_hand"), rs.getInt("reserved")))
                .optional();
    }

    @Override
    public void restock(String sku, int quantity) {
        jdbc.sql("UPDATE stock SET on_hand = on_hand + :q, updated_at = now() WHERE sku = :sku")
                .param("q", quantity)
                .param("sku", sku)
                .update();
    }
}
