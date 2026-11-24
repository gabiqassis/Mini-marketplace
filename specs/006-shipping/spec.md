# Spec 006 - Envio e rastreio (shipping-service)

## Requisitos
- RF-601 Um envio por (pedido, vendedor), reservado no `order.confirmed`; etiqueta so com NF-e autorizada.
- RF-602 Evento de nota repetido nao gera segunda etiqueta.
- RF-603 Webhook da transportadora com HMAC-SHA256 (timestamp.corpo), tolerancia de 5 min e dedupe por eventId.
- RF-604 Transicoes validas: ver ADR-0016. Fora de ordem = ignorado e guardado na linha do tempo (accepted=false).
- RF-605 `GET /shipments/{orderId}` mostra cada envio com a linha do tempo (status, local, hora); cache Redis 60 s.
- RF-606 Cada mudanca de status publica `shipping.status-changed.v1`.

## Rastreabilidade
`ShipmentTest`, `WebhookSignatureTest`, `ShipmentServiceTest`, `e2e test_03*` e `test_04*`.
