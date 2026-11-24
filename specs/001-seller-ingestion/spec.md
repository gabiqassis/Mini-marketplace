# Spec 001 - Vendedor publica itens e estoque (ingestion + catalog)

## Requisitos
- RF-001 O vendedor envia lotes pela fila AWS SQS `seller-batches` ou por `POST /sellers/{id}/batches`.
- RF-002 Lote = cabecalho (batchId, sellerId, razao social, CNPJ valido, e-mail) + ate 500 itens.
- RF-003 Cabecalho invalido rejeita o lote inteiro (API: 400; SQS: vai para `seller-batches-dlq` com o motivo).
- RF-004 Item invalido e rejeitado sozinho, com motivo legivel; os validos seguem.
- RF-005 Reenvio do mesmo (sellerId, batchId) nao publica nada de novo (idempotente).
- RF-006 Cada item tem `sequence` crescente por SKU: atualizacao antiga nunca sobrescreve a nova.
- RF-007 Itens aceitos viram `seller.item-published.v1`; o catalogo publica `catalog.item-upserted.v1`
  (consumido por estoque e pela copia de preco do pedido). Leitura do item com cache Redis.

## Cenarios
- Dado um lote valido no SQS, quando processado, entao catalogo e estoque refletem os itens.
- Dado o mesmo lote enviado duas vezes, entao o item e publicado uma unica vez.
- Dado um item com preco negativo, entao o relatorio do lote traz "preco" como motivo e o resto entra.
- Dado um lote com sellerId invalido, entao ele vai para a DLQ do SQS.
- Dado sequence 2 processada, quando chega a sequence 1, entao nada muda.

## Rastreabilidade
`BatchValidatorTest`, `IngestSellerBatchUseCaseTest`, `SellerItemIngestionServiceTest`,
`e2e/test_01_seller_ingestion.py`.
