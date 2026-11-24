package br.com.marketplace.messaging;

/** Envia uma linha do outbox para um broker. Deve lancar excecao se o broker nao confirmou. */
public interface OutboxTransport {

    OutboxRepository.Destination destination();

    void send(OutboxRepository.Row row);
}
