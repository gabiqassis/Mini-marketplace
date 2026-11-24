package br.com.marketplace.notification.application.port.out;

/** Porta de saida: envio de e-mail (SMTP local = Mailpit; producao = SES/SendGrid). */
public interface Mailer {

    void send(String to, String subject, String body);
}
