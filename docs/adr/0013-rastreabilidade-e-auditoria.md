# ADR-0013: Rastreabilidade ponta a ponta (logs, correlacao e auditoria)

**Status:** aceito

## Decisao
- Envelope v2: `messageId, type, occurredAt, correlationId, causationId, producer, payload`.
  `correlationId` = orderId (compra) ou batchId (vendedor); `causationId` = mensagem que causou esta.
  Toda mensagem nova herda a correlacao da que a causou (`Correlation.from(envelope)`).
- **Logs JSON** (formato logstash do Spring Boot) com `correlationId`, `messageId` e `eventType` no MDC
  (`LogContext`). Qualquer agregador (Datadog, Loki, ELK) filtra o pedido em todos os servicos.
- **Datadog opcional** (`docker-compose.datadog.yml`): o agente coleta os logs JSON dos containers.
  Busca: `@correlationId:<orderId>`. Sem conta Datadog, o sistema funciona igual.
- **audit-service**: consome TODOS os topicos de negocio por padrao (topico novo entra sozinho), grava
  em tabela imutavel (trigger bloqueia UPDATE/DELETE) indexada por pedido, SKU, vendedor e correlacao.
  `GET /audit/orders/{id}` devolve a linha do tempo do pedido e, para cada SKU, o lado do vendedor
  (lote, item publicado, catalogo) - do item no estoque ate a entrega.

## Consequencias
- Trilha de negocio independe do fornecedor de observabilidade.
- Proximo passo: tracing distribuido (OpenTelemetry) propagando `traceparent` no envelope.
