# ADR-0007: SQL explícito com JdbcClient e Flyway

**Status:** aceita

## Contexto
O projeto depende de recursos do PostgreSQL que um ORM esconde: `INSERT ... ON CONFLICT`, `SELECT ... FOR UPDATE SKIP LOCKED`, `CHECK` e `UNIQUE` como última defesa, `UPDATE` guardado por estado. Essas peças **são** a idempotência e a SAGA.

## Decisão
- **SQL escrito à mão** com `JdbcClient` (Spring), apenas na camada `infra` (regra ArchUnit `SQL_ONLY_IN_INFRA`).
- **Flyway** versiona o schema. `V1` (outbox/inbox) vem da lib `messaging-support`; a de cada serviço começa em `V2`.
- Banco como segunda linha de defesa: `CHECK (reserved <= on_hand)`, `UNIQUE (order_id)`, `UNIQUE (idempotency_key)`.

## Alternativas consideradas
- **JPA/Hibernate:** menos código para CRUD, mas `ON CONFLICT`, `SKIP LOCKED` e UPDATE condicional ficariam em `@Query` nativo de qualquer forma, e o *dirty checking* esconde quando e como o banco é escrito.
- **jOOQ:** SQL tipado e ótimo, mas gera código e soma uma dependência a mais para o tamanho do projeto.

## Consequências
- ➕ Cada garantia de concorrência está visível numa linha de SQL.
- ➖ Mapeamento manual de `ResultSet` e mais linhas por repositório.
