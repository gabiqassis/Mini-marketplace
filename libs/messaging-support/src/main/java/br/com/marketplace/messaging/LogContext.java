package br.com.marketplace.messaging;

import br.com.marketplace.contracts.EventEnvelope;
import org.slf4j.MDC;

/**
 * Coloca o fio de rastreabilidade no MDC enquanto a mensagem e processada. Todo log JSON sai com
 * correlationId, messageId e eventType: no Datadog/Loki basta filtrar por @correlationId:&lt;orderId&gt;.
 *
 * <pre>try (var ignored = LogContext.of(envelope)) { ... }</pre>
 */
public final class LogContext implements AutoCloseable {

    public static final String CORRELATION_ID = "correlationId";
    public static final String MESSAGE_ID = "messageId";
    public static final String EVENT_TYPE = "eventType";

    private LogContext() {}

    public static LogContext of(EventEnvelope envelope) {
        if (envelope.correlationId() != null) {
            MDC.put(CORRELATION_ID, envelope.correlationId());
        }
        MDC.put(MESSAGE_ID, String.valueOf(envelope.messageId()));
        MDC.put(EVENT_TYPE, envelope.type());
        return new LogContext();
    }

    public static LogContext of(String correlationId) {
        MDC.put(CORRELATION_ID, correlationId);
        return new LogContext();
    }

    @Override
    public void close() {
        MDC.remove(CORRELATION_ID);
        MDC.remove(MESSAGE_ID);
        MDC.remove(EVENT_TYPE);
    }
}
