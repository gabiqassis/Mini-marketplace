# Spec 003 - Pedido, SAGA e pos-venda (order-service)

## Requisitos
- RF-301 `POST /orders` exige `Idempotency-Key`; mesma chave + mesmo corpo = mesmo pedido (200);
  mesma chave + outro corpo = 422.
- RF-302 Preco, titulo e vendedor vem da copia local do catalogo, nunca do cliente; SKU desconhecido = 422.
- RF-303 Endereco valido (CEP 8 digitos, UF) e e-mail obrigatorios. Rajada acima de 30/min por comprador = 429.
- RF-304 SAGA: reservar estoque -> autorizar pagamento -> confirmar (baixa estoque).
  Sem estoque: CANCELLED sem cobrar. Pagamento recusado: compensa (libera reserva) e cancela.
- RF-305 Confirmado: uma parte por vendedor; um comando "emitir NF-e" por vendedor (RabbitMQ);
  publica `order.confirmed.v1`.
- RF-306 Todas as notas autorizadas -> pede captura (uma vez). Nota rejeitada -> ON_HOLD, sem captura.
- RF-307 Status do envio de cada parte move o pedido (INVOICED, IN_DELIVERY, DELIVERED,
  PARTIALLY_DELIVERED). Extravio: estorno da parte. Devolucao: estorno + reposicao de estoque.
- RF-308 Pagamento autorizado depois do cancelamento (timeout) e estornado automaticamente.
- RF-309 Evento repetido, atrasado ou fora de ordem nao altera o pedido.

## Maquina de estados
Ver `OrderStatus` e ADR-0004.

## Rastreabilidade
`OrderStatusTest`, `OrderProgressTest`, `FulfillmentTest`, `OrderTest`, `PlaceOrderServiceTest`,
`SagaCoordinatorTest`, `FulfillmentCoordinatorTest`, `e2e/test_02*`, `test_03*`, `test_04*`.
