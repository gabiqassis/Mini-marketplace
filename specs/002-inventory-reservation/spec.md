# 002 — Estoque: reserva, baixa e liberação

**Serviço:** `inventory-service`

## Objetivo
Garantir que nunca se venda mais do que existe, mesmo com pedidos concorrentes e mensagens repetidas, e que uma reserva possa ser desfeita (compensação da SAGA).

## Requisitos
- **RF-001** `stock(on_hand, reserved)`; disponível = `on_hand − reserved`. O banco impede `on_hand < 0`, `reserved < 0` e `reserved > on_hand`.
- **RF-002** `inventory.reserve-requested.v1`: reservar **todas** as linhas ou **nenhuma**. Sucesso → `stock-reserved`. Sku desconhecido ou falta de estoque → `reservation-rejected` (`UNKNOWN_SKU:<sku>` / `INSUFFICIENT_STOCK:<sku>`).
- **RF-003** Linhas travadas em ordem de `sku` (`FOR UPDATE`) para evitar deadlock entre pedidos.
- **RF-004** Um pedido tem **no máximo uma reserva** (`reservations.order_id` PK). Pedido repetido recebe a **mesma resposta**, sem reservar de novo.
- **RF-005** `commit-requested`: se `RESERVED`, baixa definitiva (`on_hand` e `reserved` diminuem) e vira `COMMITTED`. Qualquer outro estado: ignora.
- **RF-006** `release-requested` (compensação): se `RESERVED`, devolve a reserva e vira `RELEASED`; sempre responde `stock-released` (idempotente). Se `COMMITTED`, recusa e registra.
- **RF-007** Se o `release` chega **antes** do `reserve`, grava lápide `RELEASED`; a reserva tardia é recusada.
- **RF-008** Estoque do vendedor (`item-upserted`) só avança com `sequence` maior e nunca fica abaixo do reservado.
- **RF-009** `GET /stock/{sku}`.

## Cenários de aceite
1. **Reserva feliz** — estoque 5, pedido de 2 → `reserved=2`, evento `stock-reserved`.
2. **Sem estoque** — pedido de 99 → `reservation-rejected`, estoque intacto.
3. **Tudo ou nada** — pedido com A (ok) e B (insuficiente) → nada é reservado.
4. **Repetição** — mesmo `orderId` em duas mensagens → uma reserva, mesma resposta.
5. **Compensação** — reserva feita, depois `release` → `reserved` volta e `stock-released` é emitido (também se repetido).
6. **Release antes de reserve** — a reserva que chega depois é rejeitada.
7. **Confirmação** — `commit` após reserva → `on_hand` cai da quantidade, `reserved` volta a 0.

## Rastreabilidade
- Código: `ReservationService`, `StockSyncService`, `JdbcStockRepository`, `JdbcReservationRepository`; migration `V2__inventory.sql` (CHECKs).
- Provas: execução ponta a ponta (cenários 1, 2, 5, 7); `ArchitectureTest`.
- Contrato: `asyncapi.yaml` (canais `inventory.*`).

## v3 - Reposicao por devolucao
- RF-210 Envio devolvido ao vendedor gera `inventory.restock-requested.v1`; o estoque fisico (on_hand)
  volta, uma unica vez por (pedido, vendedor). Extravio NAO repoe (a mercadoria se perdeu).
- Rastreabilidade: `ReservationServiceTest.restockHappensOncePerOrderAndSeller`, `e2e test_returned_shipment...`.
