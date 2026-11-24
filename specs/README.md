# Specs (Spec-Driven Development)

Aqui mora **o que o sistema deve fazer**. O código é a implementação; se os dois discordarem, a spec vence e o código é o bug.

## Fluxo para mudar qualquer coisa

1. Ler a [constituição](constitution.md) — o que nunca pode ser quebrado.
2. Alterar (ou criar) a `spec.md` da feature: requisitos `RF-xxx` e cenários *Dado/Quando/Então*.
3. Se mexe em mensagem ou endpoint: alterar [`contracts/asyncapi.yaml`](contracts/asyncapi.yaml) / [`contracts/openapi.yaml`](contracts/openapi.yaml).
4. Escrever o teste que prova o cenário (a seção *Rastreabilidade* de cada spec aponta onde).
5. Só então implementar.

## Índice

| Spec | Serviço | Assunto |
|---|---|---|
| [001](001-seller-ingestion/spec.md) | ingestion, catalog | Vendedor publica itens e estoque (SQS/API); cache de leitura |
| [002](002-inventory-reservation/spec.md) | inventory | Estoque, reserva, baixa, liberação e reposição |
| [003](003-order-saga/spec.md) | order | Pedido, `Idempotency-Key`, SAGA e pós-venda |
| [004](004-payment/spec.md) | payment | Autorização, captura, estorno, estado desconhecido |
| [005](005-invoice/spec.md) | invoice + Lambda | NF-e por vendedor |
| [006](006-shipping/spec.md) | shipping | Envio, webhooks, rastreio |
| [007](007-notification-audit/spec.md) | notification, audit | Avisos e trilha ponta a ponta |
| [contracts](contracts) | todos | AsyncAPI (Kafka), RabbitMQ (filas) e OpenAPI (REST) |

## O que é cobrado automaticamente hoje

- Tópicos do código = canais do AsyncAPI = script de criação de tópicos = filas do RabbitMQ (`TopicsMatchSpecTest`).
- Transições do pedido = diagrama da spec 003 (`OrderStatusTest`).
- Camadas e dependências = constituição (`ArchitectureTest` em cada serviço).

- Comportamento ponta a ponta de cada cenário = suíte `e2e/` (pytest), rodada por `python scripts/tdd.py e2e`.

O que ainda é só revisão humana: o formato dos **payloads** contra o AsyncAPI (próximo passo: teste de contrato com JSON Schema).
