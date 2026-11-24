# ADR-0003: Outbox transacional (Kafka e RabbitMQ), inbox e idempotencia em 3 niveis

**Status:** aceito (revisado na v3)

## Contexto
"Gravar no banco e publicar no broker" sao duas operacoes: se o processo cai entre elas, o dado e a
mensagem divergem (dual-write). E todo broker entrega **at-least-once**: o exactly-once do Kafka vale
dentro do Kafka, nao ate o banco do consumidor.

## Decisao
1. **Outbox** em todo servico: a mensagem e gravada na tabela `outbox` na MESMA transacao do dado.
   Um relay agendado publica (`FOR UPDATE SKIP LOCKED`, varias instancias sem disputa). A coluna
   `destination` diz se vai para o **Kafka** (fato) ou **RabbitMQ** (comando, com publisher confirms).
2. **Inbox** em todo consumidor: `INSERT (message_id, consumer) ON CONFLICT DO NOTHING` na mesma
   transacao do efeito. Mensagem repetida nao faz nada.
3. **Chaves de negocio UNIQUE** (a defesa final, mesmo com mensagens diferentes):
   `orders.idempotency_key` (+ hash do corpo), `reservations.order_id`, `payments.order_id`,
   `payment_ledger(payment, tipo, referencia)`, `invoices(order_id, seller_id)`,
   `shipments(order_id, seller_id)`, `carrier_events.event_id`, `notifications.dedupe_key`,
   `batches(seller_id, batch_id)`, `audit_events.message_id`.
4. Efeito externo que nao tem rollback (cobranca, e-mail, NF-e): chave de idempotencia estavel na
   chamada (`auth:{orderId}`, numero da nota reservado antes) e trava curta no Redis para e-mail.

## Consequencias
- Nenhuma mensagem se perde com broker fora: fica no outbox e sai quando ele volta (testado no caos).
- Latencia extra de ate ~200 ms (polling). Em volume alto: CDC (Debezium).
- outbox/inbox crescem: purge diario das linhas publicadas ha mais de 7 dias.
