# ADR-0002: Kafka como backbone de mensageria

**Status:** aceita

## Contexto
O vendedor envia itens e estoque de forma assíncrona e em volume; os serviços precisam trocar comandos e eventos da SAGA. A fila precisa permitir reprocessar, manter ordem onde importa e isolar mensagens com defeito.

## Decisão
**Apache Kafka** (modo KRaft, sem Zookeeper). Convenções:
- Tópico `<contexto>.<fato>.v1` (versão no nome); uma DLT `<tópico>.DLT` para cada um, com as mesmas 3 partições.
- **Chave = identificador do agregado** (`sku` no catálogo, `orderId` na SAGA): todas as mensagens do mesmo pedido/item vão para a mesma partição, então chegam em ordem.
- Todo valor é um **envelope** `{messageId, type, occurredAt, payload}`, inclusive o que o vendedor envia.
- Produtor com `acks=all` e idempotência ligada; consumidor com commit manual por registro.

## Alternativas consideradas
- **SQS:** serviço gerenciado, ótimo para filas simples e já usado no dia a dia (Lambda + SQS). Mas não garante ordem por chave sem FIFO/grupos, e não permite reler o histórico (replay). Para um marketplace em que o estoque do vendedor precisa de ordem por `sku`, Kafka encaixa melhor. O desenho de inbox/outbox serviria igual para SQS.
- **RabbitMQ:** bom roteamento, mas sem log retido/replay.

## Consequências
- ➕ Ordem por chave, replay, DLT e vários consumidores do mesmo evento (`item-upserted` alimenta inventário **e** pedidos).
- ➖ Entrega **at-least-once**: toda mensagem pode chegar duas vezes → idempotência obrigatória (ADR-0003).
- ➖ Mais pesado de rodar localmente que uma fila simples.
