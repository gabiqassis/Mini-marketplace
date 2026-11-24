package br.com.marketplace.notification.domain;

/** Notificacao a ser criada: para quem (vendedor/comprador), qual modelo, assunto e texto. */
public record Draft(Recipient recipient, String recipientId, String template, String subject, String body) {

    public enum Recipient {
        SELLER,
        BUYER
    }
}
