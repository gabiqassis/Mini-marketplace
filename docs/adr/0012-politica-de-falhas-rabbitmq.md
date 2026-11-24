# ADR-0012: Politica de falhas dos consumidores RabbitMQ

**Status:** aceito

## Decisao (`RetryingRabbitHandler`, em messaging-support)
1. Sucesso -> ack.
2. Falha **transitoria** -> republica em `<fila>.retry` com `expiration` = backoff exponencial com
   jitter e header `x-attempt`; ao expirar, o DLX devolve para `<fila>`. Ack da original.
3. Falha **permanente** (`InvalidMessageException`, ex.: comando incompleto) ou tentativas esgotadas
   -> `<fila>.parking` (com o motivo) + callback de negocio (ex.: nota FAILED + `invoice.rejected`).
4. Se ate o republish falhar, a excecao sobe, a mensagem volta e o `x-delivery-limit` da fila quorum
   impede loop infinito (vai para parking).

Rejeicao **fiscal** da NF-e (CNPJ irregular) nao e excecao: e um resultado de negocio, publicado
como `invoice.rejected` sem retry.

## Consequencias
- Retry nao bloqueia o consumidor (espera fica na fila `.retry`, nao em `Thread.sleep`).
- Mensagens em parking precisam de acao humana (reprocessar apos corrigir).
- Achado no E2E: o callback de desistencia falhava ao gravar uma mensagem de erro longa demais; o
  motivo agora e truncado no dominio (`Invoice.shorten`) e ha teste unitario para isso.
