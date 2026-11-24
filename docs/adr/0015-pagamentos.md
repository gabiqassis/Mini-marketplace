# ADR-0015: Pagamentos - autorizacao, captura, estado desconhecido e ledger

**Status:** aceito

## Decisao
- **Autoriza** na SAGA; **captura** so quando todas as notas foram autorizadas; **VOID** se cancelar
  antes da captura; **REFUND** (parcial ou total) depois.
- Chave de idempotencia estavel por operacao (`auth:{orderId}`, `capture:{orderId}`,
  `refund:{orderId}:{referencia}`): repetir a chamada nunca cobra duas vezes.
- Chamada ao gateway **fora da transacao** do banco; resultado gravado numa transacao curta.
- **Timeout = estado DESCONHECIDO** (`AUTHORIZING`), nunca recusa. A reconciliacao (a cada 5 s)
  consulta o gateway pela chave original; se ele nunca recebeu, reenvia com a MESMA chave; apos 5
  tentativas, recusa (`GATEWAY_NO_RESPONSE`).
- Circuito aberto ou falha antes de enviar = `UNAVAILABLE` (seguro recusar: a requisicao nao saiu).
- **Ledger append-only** (`payment_ledger`, trigger proibe UPDATE/DELETE) com
  `UNIQUE(payment, tipo, referencia)`; invariante `0 <= estornado <= capturado <= autorizado` em CHECK.
- Estorno recalculado sob lock (dois estornos paralelos nao passam do capturado).
- Seguranca (producao): tokenizacao no front (o servico nunca ve PAN/CVV), nada sensivel em log,
  segredos fora do codigo, TLS.

## Consequencias
O adquirente e simulado (`SimulatedAcquirer`) com as mesmas regras de idempotencia de um real; trocar
por um gateway real e implementar a porta `PaymentGateway`.
