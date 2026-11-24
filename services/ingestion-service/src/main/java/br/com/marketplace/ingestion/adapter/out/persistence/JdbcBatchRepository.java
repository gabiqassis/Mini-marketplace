package br.com.marketplace.ingestion.adapter.out.persistence;

import br.com.marketplace.ingestion.domain.BatchReport;
import br.com.marketplace.ingestion.application.port.out.BatchRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBatchRepository implements BatchRepository {

    private final JdbcClient jdbc;

    JdbcBatchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean saveIfAbsent(BatchReport r) {
        int inserted = jdbc.sql("""
                        INSERT INTO batches (seller_id, batch_id, source, accepted, rejected)
                        VALUES (:seller, :batch, :source, :acc, :rej)
                        ON CONFLICT (seller_id, batch_id) DO NOTHING
                        """)
                .param("seller", r.sellerId())
                .param("batch", r.batchId())
                .param("source", r.source())
                .param("acc", r.accepted())
                .param("rej", r.rejected())
                .update();
        if (inserted == 0) {
            return false;
        }
        int position = 0;
        for (BatchReport.ItemResult item : r.items()) {
            jdbc.sql("""
                            INSERT INTO batch_items (seller_id, batch_id, position, sku, status, reason)
                            VALUES (:seller, :batch, :pos, :sku, :status, :reason)
                            """)
                    .param("seller", r.sellerId())
                    .param("batch", r.batchId())
                    .param("pos", position++)
                    .param("sku", item.sku())
                    .param("status", item.status())
                    .param("reason", item.reason())
                    .update();
        }
        return true;
    }

    @Override
    public Optional<BatchReport> find(String sellerId, String batchId) {
        List<BatchReport.ItemResult> items = jdbc.sql("""
                        SELECT sku, status, reason FROM batch_items
                         WHERE seller_id = :seller AND batch_id = :batch ORDER BY position
                        """)
                .param("seller", sellerId)
                .param("batch", batchId)
                .query((rs, n) -> new BatchReport.ItemResult(rs.getString("sku"), rs.getString("status"), rs.getString("reason")))
                .list();
        return jdbc.sql("SELECT source, accepted, rejected FROM batches WHERE seller_id = :seller AND batch_id = :batch")
                .param("seller", sellerId)
                .param("batch", batchId)
                .query((rs, n) -> new BatchReport(sellerId, batchId, rs.getString("source"), rs.getInt("accepted"),
                        rs.getInt("rejected"), items))
                .optional();
    }
}
