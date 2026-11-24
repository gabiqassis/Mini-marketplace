# Constituição do Mini Marketplace

Princípios **inegociáveis**. Cada um tem uma trava executável (ver [ADR-0009](../docs/adr/0009-travas-de-arquitetura.md)).

| # | Princípio | Trava |
|---|---|---|
| C1 | **Dados por serviço.** Nenhum serviço lê ou escreve no schema de outro. | Um schema + um usuário por serviço, sem `USAGE` cruzado; Enforcer proíbe dependência entre serviços |
| C2 | **Comunicação só por mensagens** (Kafka = fatos, RabbitMQ = comandos; REST apenas para clientes externos e webhooks). | Sem dependência entre serviços; sem client HTTP interno |
| C3 | **Nenhum dual-write.** Mensagem (Kafka ou RabbitMQ) sai sempre pelo **outbox**, na mesma transação do dado. | `OutboxRepository.publish/send` é o único jeito de publicar |
| C4 | **Todo consumidor é idempotente:** inbox por `messageId` **e** chave de negócio. | `PRIMARY KEY` no inbox; `UNIQUE`/PK de negócio nas tabelas |
| C5 | **Toda mensagem usa o envelope** `{messageId, type, occurredAt, correlationId, causationId, producer, payload}`. | `Envelopes.read` rejeita o resto (→ DLT/parking) |
| C6 | **Mudança de estado é guardada:** `UPDATE ... WHERE status = <esperado>`. Só existem as transições do desenho. | `OrderStatus` + `OrderStatusTest` |
| C7 | **Domínio puro:** regra de negócio sem Spring, Kafka ou SQL. | ArchUnit `DOMAIN_IS_PURE` |
| C8 | **Camadas:** `api → application → domain ← infra`. SQL, `@KafkaListener`, `@RabbitListener` e AWS SDK só em `infra`. | ArchUnit |
| C9 | **O banco é a última defesa:** `CHECK`, `UNIQUE`, `FK` onde fizer sentido. | Migrations Flyway |
| C10 | **Chamada externa nunca dentro de transação de banco**, sempre com timeout e chave de idempotência. | `PaymentService` → `PaymentRecorder`; `IssueInvoiceUseCase` → `InvoiceRecorder` |
| C11 | **Falha tem destino:** retry com backoff e jitter → DLT/parking; nada é descartado em silêncio. | `MessagingAutoConfiguration`, `RetryingRabbitHandler` |
| C12 | **Redis é opcional:** o sistema funciona sem ele (mais lento). | Adaptadores Redis capturam falhas; teste de caos |
| C13 | **Contrato versionado** (`.v1`) e **leitor tolerante** (campo desconhecido é ignorado). | Nome do tópico; `TopicsMatchSpecTest` |
| C14 | **Spec primeiro.** | Processo + `TopicsMatchSpecTest` |
| C15 | **Rastreável:** todo fato tem correlationId e vai para a trilha imutável. | `LogContext`, audit-service (trigger append-only) |
