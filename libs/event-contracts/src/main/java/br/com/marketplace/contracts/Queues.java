package br.com.marketplace.contracts;

/**
 * Filas RabbitMQ (COMANDOS e JOBS com retry). Topologia declarada em infra/rabbitmq/definitions.json,
 * entao as filas existem mesmo com o consumidor fora do ar: o comando espera, nao se perde.
 *
 * <p>Para cada fila X existem: X.retry (espera com TTL e volta para X) e X.parking (esgotou tentativas).
 */
public final class Queues {

    private Queues() {}

    public static final String COMMANDS_EXCHANGE = "marketplace.commands";
    public static final String RETRY_EXCHANGE = "marketplace.retry";
    public static final String PARKING_EXCHANGE = "marketplace.parking";

    /** Pedidos manda emitir NF-e (um comando por vendedor do pedido). */
    public static final String INVOICE_ISSUE = "invoice.issue";

    /** Job de envio de e-mail/push (notification-service produz e consome). */
    public static final String NOTIFICATION_SEND = "notification.send";

    public static String retryOf(String queue) {
        return queue + ".retry";
    }

    public static String parkingOf(String queue) {
        return queue + ".parking";
    }
}
