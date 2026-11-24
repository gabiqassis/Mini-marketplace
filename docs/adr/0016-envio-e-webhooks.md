# ADR-0016: Envio, rastreio e webhooks da transportadora

**Status:** aceito

## Decisao
- Um envio por (pedido, vendedor), criado no `order.confirmed` como `AWAITING_INVOICE`. A etiqueta
  (codigo de rastreio) so e gerada com a NF-e autorizada: **sem nota, sem envio** (exigencia fiscal).
  `UNIQUE(order_id, seller_id)` + transicao de dominio impedem etiqueta duplicada.
- Maquina de estados: `CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED`,
  `DELIVERY_FAILED` (conta tentativas) `-> OUT_FOR_DELIVERY | RETURNED`, `LOST` de qualquer estado em
  movimento. Mesmo status com novo local = checkpoint (vai para a linha do tempo).
- Evento fora de ordem e **ignorado mas guardado** (`accepted=false`) para auditoria.
- Webhook: HMAC-SHA256 sobre `timestamp.corpoBruto`, tolerancia de 5 min (anti-replay), comparacao em
  tempo constante, dedupe por `eventId`. Respostas: 401 assinatura, 200 duplicado (pare de reenviar),
  202 aceito, 404 rastreio desconhecido.
- Rastreio do comprador com cache Redis (60 s), invalidado apos cada mudanca.
- Extravio -> estorno; devolucao -> estorno + reposicao do estoque (order-service decide).

## Consequencias
Transportadora simulada pelos proprios testes (mesmo formato de um webhook real). Fallback para uma
segunda transportadora fica como evolucao.
