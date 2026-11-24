package br.com.marketplace.messaging.rabbit;

import br.com.marketplace.messaging.InvalidMessageException;

/** Decisao pura (testavel): tentar de novo ou desistir. */
public record RabbitRetryPolicy(int maxAttempts) {

    public enum Decision {
        RETRY,
        GIVE_UP
    }

    /** @param attempt tentativa que acabou de falhar (0 = primeira) */
    public Decision decide(int attempt, Throwable failure) {
        if (failure instanceof InvalidMessageException) {
            return Decision.GIVE_UP;
        }
        return attempt + 1 < maxAttempts ? Decision.RETRY : Decision.GIVE_UP;
    }
}
