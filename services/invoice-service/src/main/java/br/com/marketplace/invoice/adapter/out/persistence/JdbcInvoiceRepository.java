package br.com.marketplace.invoice.adapter.out.persistence;

import br.com.marketplace.invoice.domain.Invoice;
import br.com.marketplace.invoice.application.port.out.InvoiceRepository;
import br.com.marketplace.invoice.domain.SellerFiscalProfile;
import br.com.marketplace.invoice.application.port.out.SellerProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInvoiceRepository implements InvoiceRepository, SellerProfileRepository {

    private static final int SERIES = 1;

    private static final RowMapper<Invoice> MAPPER = (rs, n) -> new Invoice(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getString("seller_id"),
            rs.getInt("series"),
            rs.getLong("number"),
            rs.getBigDecimal("amount"),
            Invoice.InvoiceStatus.valueOf(rs.getString("status")),
            rs.getString("access_key"),
            rs.getString("protocol"),
            rs.getString("rejection_code"),
            rs.getString("rejection_reason"));

    private final JdbcClient jdbc;

    JdbcInvoiceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Invoice> find(UUID orderId, String sellerId) {
        return jdbc.sql("SELECT * FROM invoices WHERE order_id = :o AND seller_id = :s")
                .param("o", orderId).param("s", sellerId).query(MAPPER).optional();
    }

    @Override
    public Invoice reserve(UUID orderId, String sellerId, BigDecimal amount) {
        Optional<Invoice> existing = find(orderId, sellerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        // Numeracao por vendedor: UPDATE ... RETURNING serializa concorrentes na mesma linha.
        jdbc.sql("INSERT INTO invoice_counters (seller_id, series, last_number) VALUES (:s, :series, 0) ON CONFLICT DO NOTHING")
                .param("s", sellerId).param("series", SERIES).update();
        long number = jdbc.sql("""
                        UPDATE invoice_counters SET last_number = last_number + 1
                         WHERE seller_id = :s AND series = :series RETURNING last_number
                        """)
                .param("s", sellerId).param("series", SERIES).query(Long.class).single();
        jdbc.sql("""
                        INSERT INTO invoices (id, order_id, seller_id, series, number, amount, status)
                        VALUES (:id, :o, :s, :series, :number, :amount, 'ISSUING')
                        """)
                .param("id", UUID.randomUUID()).param("o", orderId).param("s", sellerId)
                .param("series", SERIES).param("number", number).param("amount", amount)
                .update();
        return find(orderId, sellerId).orElseThrow();
    }

    @Override
    public void update(Invoice i) {
        jdbc.sql("""
                        UPDATE invoices SET status = :status, access_key = :key, protocol = :proto,
                               rejection_code = :code, rejection_reason = :reason, updated_at = now()
                         WHERE id = :id
                        """)
                .param("status", i.status().name()).param("key", i.accessKey()).param("proto", i.protocol())
                .param("code", i.rejectionCode()).param("reason", i.rejectionReason()).param("id", i.id())
                .update();
    }

    @Override
    public List<Invoice> findByOrder(UUID orderId) {
        return jdbc.sql("SELECT * FROM invoices WHERE order_id = :o ORDER BY seller_id").param("o", orderId).query(MAPPER).list();
    }

    @Override
    public void upsert(SellerFiscalProfile p) {
        jdbc.sql("""
                        INSERT INTO seller_profiles (seller_id, legal_name, cnpj) VALUES (:s, :name, :cnpj)
                        ON CONFLICT (seller_id) DO UPDATE SET legal_name = EXCLUDED.legal_name, cnpj = EXCLUDED.cnpj, updated_at = now()
                        """)
                .param("s", p.sellerId()).param("name", p.legalName()).param("cnpj", p.cnpj()).update();
    }

    @Override
    public Optional<SellerFiscalProfile> find(String sellerId) {
        return jdbc.sql("SELECT seller_id, legal_name, cnpj FROM seller_profiles WHERE seller_id = :s")
                .param("s", sellerId)
                .query((rs, n) -> new SellerFiscalProfile(rs.getString("seller_id"), rs.getString("legal_name"), rs.getString("cnpj")))
                .optional();
    }
}
