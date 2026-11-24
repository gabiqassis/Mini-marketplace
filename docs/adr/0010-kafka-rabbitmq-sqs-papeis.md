# ADR-0010: Kafka, RabbitMQ e SQS - cada um no seu papel

**Status:** aceito

## Contexto
Tres brokers parecem redundancia. A justificativa precisa ser o tipo de mensagem, nao gosto.

## Decisao
| | Kafka | RabbitMQ | SQS |
|---|---|---|---|
| Tipo | **fato** ("aconteceu") | **comando/job** ("faca") | **entrada externa** |
| Consumidores | muitos (pedido, envio, notificacao, auditoria leem o mesmo fato) | um worker por fila | um (ingestion) |
| Historico/replay | sim (log retido) | nao (some apos ack) | nao |
| Retry | DLT apos 4 tentativas | TTL por mensagem + DLX + parking | redrive policy (5x) + DLQ |
| Exemplos | `order.confirmed.v1`, `shipping.status-changed.v1` | `invoice.issue`, `notification.send` | `seller-batches` |

- Ordem por chave no Kafka (orderId/sku); topico novo so via contrato (auto-create desligado).
- RabbitMQ com **filas quorum** (replicadas), `x-delivery-limit` e dead-lettering at-least-once;
  topologia carregada no boot (`definitions.json`): o comando espera mesmo com o consumidor fora.
- SQS e a porta que um vendedor externo (ou integrador) usaria na AWS.

## Consequencias
Mais infraestrutura para operar. Em troca: auditoria e novos consumidores de graca (Kafka), controle
fino de retry e vazao para trabalho lento (RabbitMQ), e integracao nativa AWS para o vendedor (SQS).
