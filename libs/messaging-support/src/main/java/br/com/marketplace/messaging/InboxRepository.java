package br.com.marketplace.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Idempotencia do consumidor. Chame DENTRO da mesma transacao do efeito de negocio. */
public class InboxRepository {

    private final JdbcClient jdbc;

    public InboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true na primeira vez; false se este consumidor ja processou a mensagem. */
    public boolean markProcessed(UUID messageId, String consumer) {
        return jdbc.sql("INSERT INTO inbox (message_id, consumer) VALUES (:m, :c) ON CONFLICT DO NOTHING")
                        .param("m", messageId)
                        .param("c", consumer)
                        .update()
                == 1;
    }
}
