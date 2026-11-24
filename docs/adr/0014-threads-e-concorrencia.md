# ADR-0014: Threads e concorrencia

**Status:** aceito

## Contexto
Java 21 oferece virtual threads. O primeiro teste do ambiente completo travou: com
`spring.threads.virtual.enabled=true`, os consumidores Kafka rodavam em virtual threads e, no JDK 21,
o cliente Kafka faz *pinning* (prende a thread carregadora). Com poucas CPUs, todas as carregadoras
ficaram presas e ate o `/actuator/health` parou de responder.

## Decisao
| Onde | Modelo | Limite |
|---|---|---|
| Consumidores Kafka | threads de plataforma | `concurrency: 3` (= particoes) por listener |
| Consumidores RabbitMQ | threads de plataforma | 2-4 por fila, `prefetch: 10` |
| Tomcat (HTTP) | threads de plataforma | padrao |
| Relay do outbox, timeout da SAGA, reconciliacao | `@Scheduled` (thread agendada) | 1 por tarefa; varias instancias seguras (SKIP LOCKED) |
| Chamadas ao gateway de pagamento | **virtual threads** + timeout | bulkhead 20 |
| Invocacao da Lambda | thread do consumidor + bulkhead | 10 (1 local) |
| Poller do SQS | **virtual thread** dedicada (long polling) | 1 |

Estado compartilhado: nenhum mutavel entre threads; concorrencia resolvida no banco (locks de linha,
UNIQUE, transicoes guardadas).

## Consequencias
Virtual threads onde ha espera de I/O sem bibliotecas que fazem pinning. Revisitar no JDK 24+
(JEP 491 remove o pinning de `synchronized`).
