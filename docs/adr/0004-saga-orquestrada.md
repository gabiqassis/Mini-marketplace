# ADR-0004: SAGA orquestrada no order-service, com pos-venda por vendedor

**Status:** aceito (revisado na v3)

## Contexto
Uma compra envolve estoque, pagamento, nota fiscal e envio, em servicos e bancos diferentes. Nao ha
transacao distribuida (2PC indisponivel entre gateway, SEFAZ e transportadora).

## Decisao
SAGA **orquestrada** (um coordenador com o estado salvo), mais facil de auditar que coreografia:

```
PENDING --estoque reservado--> AWAITING_PAYMENT --autorizado--> CONFIRMED
   |  sem estoque -> CANCELLED       |  recusado/timeout -> CANCELLING -> (libera estoque) -> CANCELLED
CONFIRMED: baixa estoque + 1 comando "emitir NF-e" por vendedor (RabbitMQ) + order.confirmed
todas as notas -> CAPTURA do pagamento -> INVOICED -> IN_DELIVERY -> DELIVERED | PARTIALLY_DELIVERED
nota rejeitada -> ON_HOLD | extravio -> estorno da parte | devolucao -> estorno + reposicao
```

- **Autorizar antes, capturar depois** das notas: sem nota, nao se cobra o cliente.
- Cada handler: inbox + **transicao guardada** (`UPDATE ... WHERE status = esperado`) + outbox.
  Evento atrasado ou fora de ordem nao muda nada.
- Eventos de um mesmo pedido vindos de topicos diferentes sao serializados com `SELECT ... FOR UPDATE`.
- Varredura de timeout compensa pedidos presos (resposta perdida, servico fora).
- **Corrida de timeout**: pagamento aprovado DEPOIS do cancelamento gera estorno automatico (VOID).
- Status pos-venda e **derivado** das partes de cada vendedor (`OrderProgress`, funcao pura testada).

## Consequencias
- Sem isolamento: outros veem estados intermediarios (PENDING, AWAITING_PAYMENT). Aceito e explicito.
- Compensacao tambem pode falhar: retry + DLT + alerta (operacao).
