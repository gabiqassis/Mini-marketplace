# ADR-0001: Microsservicos com dados isolados por servico (um banco, um schema por servico)

**Status:** aceito (revisado na v3)

## Contexto
O marketplace tem contextos com ritmos e cargas diferentes (catalogo le muito, pagamento exige
consistencia forte, auditoria so escreve). Compartilhar tabelas entre servicos cria o "monolito
distribuido": deploy acoplado e falha em cascata. Para rodar localmente, porem, 9 instancias de
PostgreSQL pesam demais no notebook.

## Decisao
- 9 servicos, cada um dono dos seus dados. Comunicacao **so por mensagens** (Kafka/RabbitMQ), nunca
  por leitura do banco do outro.
- **Um banco fisico** (`marketplace`) com **um schema e um usuario por servico**. Cada usuario e dono
  so do proprio schema e nao tem `USAGE` nos outros (isolamento garantido pelo PostgreSQL).
- Usuario `observer` somente leitura para inspecao manual.
- Maven Enforcer proibe um servico depender de outro (`br.com.marketplace:*-service`).

## Consequencias
- Trocar para um banco por servico em producao e so mudar a `DB_URL` (nada no codigo).
- Consultas que cruzam contextos viram modelos de leitura alimentados por eventos (ex.: copia de
  preco no order-service, trilha no audit-service).
- Consistencia entre servicos e eventual: tratada por SAGA (ADR-0004) e idempotencia (ADR-0003).
