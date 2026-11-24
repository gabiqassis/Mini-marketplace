# Spec 007 - Notificacoes e auditoria

## Notificacoes (notification-service)
- RF-701 Vendedor: "Voce vendeu" (itens e valor), NF-e autorizada, NF-e rejeitada (acao necessaria),
  entregue, devolucao a caminho.
- RF-702 Comprador: pedido confirmado, cancelado (com motivo humano, ex.: "o produto esgotou"),
  etiqueta/rastreio, a caminho, saiu para entrega, tentativa falhou, entregue, extraviado, devolvido, reembolsado.
- RF-703 Um aviso por (fato, destinatario, modelo): sem duplicata mesmo com reentrega.
- RF-704 Envio por fila RabbitMQ com retry; servico fora = avisos enviados quando voltar.

## Auditoria (audit-service)
- RF-711 Todo fato de negocio do Kafka e gravado uma vez, sem alteracao possivel.
- RF-712 `GET /audit/orders/{id}`: linha do tempo do pedido + historia de cada SKU no lado do vendedor.
- RF-713 Cada passo mostra producer, causationId e posicao no Kafka (topico/particao/offset).

## Rastreabilidade
`NotificationPlannerTest`, `SendNotificationWorkerTest`, `AuditIndexerTest`,
`e2e test_full_purchase...`, `test_notification_down...` (caos).
