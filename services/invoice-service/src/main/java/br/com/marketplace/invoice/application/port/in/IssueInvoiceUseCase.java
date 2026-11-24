package br.com.marketplace.invoice.application.port.in;

import br.com.marketplace.contracts.EventEnvelope;
import br.com.marketplace.contracts.Events.IssueInvoiceCommand;

/** Porta de entrada: contrato do caso de uso, implementado em IssueInvoiceService. */
public interface IssueInvoiceUseCase {

    void issue(EventEnvelope env, IssueInvoiceCommand cmd);

    void giveUp(EventEnvelope env, IssueInvoiceCommand cmd, String reason);
}
