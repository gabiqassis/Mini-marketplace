# ADR-0006: Resiliencia - um servico quebrado nao derruba os outros

**Status:** aceito (revisado na v3)

## Decisao
| Mecanismo | Onde | Por que |
|---|---|---|
| Comunicacao assincrona + outbox | todos | servico fora = mensagem espera no broker |
| Retry com backoff exponencial **com jitter** | consumidores Kafka (4x, 0,5-4 s) e RabbitMQ (5x, 1-30 s) | evita o "thundering herd" de retries sincronizados |
| DLT `<topico>.DLT` / fila `.parking` | Kafka / RabbitMQ | poison pill nao trava particao/fila |
| Erro permanente nao repete | `InvalidMessageException` | nao gasta retry com mensagem que nunca vai passar |
| Timeout | gateway 1,5 s, Lambda 10 s, Redis 200 ms, SMTP 3 s | nada espera para sempre |
| Bulkhead (Semaphore) | gateway (20), Lambda (10; 1 local) | dependencia lenta nao consome todas as threads |
| Circuit breaker (Resilience4j) | gateway de pagamento | para de chamar quem esta caindo; volta em meia-abertura |
| Fail-open | Redis (cache, rate limit) | otimizacao nao derruba o negocio |
| Graceful shutdown (20 s) | todos | fecha antes do grace period do container: sem rebalance longo |
| `CooperativeStickyAssignor` | consumidores Kafka | rebalance incremental no deploy |
| `consumerStartTimeout` 2 min | consumidores Kafka | boot lento no notebook nao derruba o consumidor |

## Provas (testes de caos)
notification fora, Redis fora, Lambda fora e inventory fora: o resto continua e se recupera.
