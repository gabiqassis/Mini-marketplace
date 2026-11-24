# Spec 005 - Nota fiscal (invoice-service + Lambda nfe-emitter)

## Requisitos
- RF-501 Um comando `invoice.issue` por (pedido, vendedor); no maximo uma nota por par.
- RF-502 Numero sequencial por vendedor e serie, reservado antes da chamada e reutilizado no retry.
- RF-503 Autorizacao pela Lambda (AWS SDK Invoke): chave de acesso de 44 digitos (DV modulo 11) e protocolo.
- RF-504 Rejeicao fiscal (207 CNPJ invalido, 203 emissor nao habilitado, 225 schema) publica
  `invoice.rejected.v1` sem retry; o vendedor e avisado.
- RF-505 Falha tecnica (Lambda/SEFAZ fora) = retry com backoff; esgotou = nota FAILED + `invoice.rejected.v1` (TECHNICAL) + parking.
- RF-506 Dados fiscais do vendedor vem de `seller.profile-updated.v1`; se ainda nao chegaram, retry.

## Rastreabilidade
`CnpjTest`, `AccessKeyTest`, `NfeEmitterTest`, `IssueInvoiceUseCaseTest`, `InvoiceTest`,
`e2e test_rejected_invoice...`, `test_lambda_down...` (caos).
