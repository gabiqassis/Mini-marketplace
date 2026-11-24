# ADR-0005: Redis para cache e coordenacao leve, nunca como fonte da verdade

**Status:** aceito (revisado na v3)

| Uso | Chave | TTL | Quando apaga | Se o Redis cair |
|---|---|---|---|---|
| Cache do item (catalog) | `catalog:item:{sku}` | 10 min + jitter 10% | item atualizado (afterCommit) | le do banco |
| Cache do rastreio (shipping) | `shipping:tracking:{orderId}` | 60 s | mudanca de status (afterCommit) | le do banco |
| Rate limit do checkout (order) | `ratelimit:orders:{buyer}:{minuto}` | 70 s | expira | deixa passar (fail-open) |
| Trava de envio de e-mail (notification) | `notification:sending:{id}` | 10 min | falha no envio | envia (status SENT no banco segura) |

## Decisao
- Cache-aside com invalidacao **depois do commit** (senao uma leitura concorrente repopula o valor velho).
- Jitter no TTL evita expirar tudo junto (cache stampede).
- Timeout de 200 ms no cliente: Redis lento nao trava o servico.
- Testado: `test_redis_down_degrades_gracefully` (caos).

## Consequencias
- Leitura pode ficar ate o TTL desatualizada se a invalidacao falhar (o TTL limita o dano).
- Filtro de estoque no Redis (Lua DECR) para promocao relampago foi desenhado mas nao implementado:
  o PostgreSQL com lock ordenado atende o volume do projeto.
