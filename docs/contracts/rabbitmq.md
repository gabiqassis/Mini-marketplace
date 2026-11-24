# Contratos RabbitMQ (comandos e jobs)

Topologia: `infra/rabbitmq/definitions.json` (carregada no boot do broker).
Toda mensagem usa o mesmo envelope do Kafka (ver asyncapi.yaml). Entrega at-least-once:
consumidores idempotentes.

| Fila | Exchange / routing key | Produtor | Consumidor | Payload |
|---|---|---|---|---|
| `invoice.issue` | `marketplace.commands` / `invoice.issue` | order-service (outbox) | invoice-service | `IssueInvoiceCommand {orderId, sellerId, buyerId, address, amount, lines[]}` |
| `notification.send` | `marketplace.commands` / `notification.send` | notification-service (outbox) | notification-service | `SendNotificationJob {notificationId}` |

Para cada fila X:

| Fila | Tipo | Papel |
|---|---|---|
| `X` | quorum, `x-delivery-limit: 10`, DLX -> `X.parking` | trabalho |
| `X.retry` | quorum, DLX -> `marketplace.commands`/`X` | espera do backoff (TTL por mensagem) |
| `X.parking` | quorum | esgotou tentativas ou erro permanente; header `x-failure-reason` |

Header `x-attempt`: numero da tentativa (0 = primeira). Maximo: 5 (configuravel).
