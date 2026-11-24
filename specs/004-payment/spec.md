# Spec 004 - Pagamento (payment-service)

## Requisitos
- RF-401 Autorizar o valor do pedido; recusa publica `payment.declined.v1` com motivo.
- RF-402 Timeout do gateway deixa o pagamento AUTHORIZING (desconhecido). A reconciliacao resolve
  consultando pela mesma chave; nunca autoriza duas vezes.
- RF-403 Capturar so o autorizado e so uma vez.
- RF-404 Estorno: antes da captura = VOID do total; depois = REFUND ate o capturado, parcial ou total,
  uma vez por referencia (ex.: vendedor do envio extraviado).
- RF-405 Todo movimento vira linha no ledger append-only.
- RF-406 Gateway instavel: timeout, bulkhead e circuit breaker; circuito aberto = recusa
  GATEWAY_UNAVAILABLE (a requisicao nao saiu).

## Regras do adquirente simulado
valor > 10.000 -> LIMIT_EXCEEDED; comprador `buyer-fraud` -> FRAUD_SUSPECTED;
comprador `buyer-timeout*` -> aprova mas responde depois do timeout.

## Rastreabilidade
`PaymentTest`, `PaymentServiceTest`, `e2e/test_04_business_failures.py` (recusa, timeout, extravio, devolucao).
