# ADR-0009: Travas de arquitetura (o build quebra se alguem violar)

**Status:** aceito (revisado na v3)

## Decisao
Cada servico e hexagonal (portas e adaptadores): `adapter.in -> application.port.in -> application.service -> application.port.out <- adapter.out`, com o `domain` no centro.

**ArchUnit** (`libs/architecture-rules`, roda em cada servico):
1. domain nao conhece Spring, Kafka, Jackson, jakarta, javax, application, adapter nem config
2. application nao conhece adapter nem config
3. adaptadores de entrada so chamam portas de entrada (nunca `application.service`, portas de saida ou adaptadores de saida)
4. adaptadores de saida nao conhecem adaptadores de entrada nem servicos
5. SQL (`JdbcClient`) so em `adapter.out.persistence`
6. `@KafkaListener` e `@RabbitListener` so em `adapter.in`
7. AWS SDK so em `adapter` e `config`
8. sem injecao por campo (`@Autowired` em atributo)
9. sem ciclos entre camadas
10. sem `System.out` / `java.util.logging`

**Maven Enforcer**: Java 21+, servico nao depende de servico, `commons-logging` banido.
**Banco**: CHECK, UNIQUE, FK e triggers append-only (a regra vale mesmo se o codigo errar).
**SDD**: `TopicsMatchContractsTest` compara codigo x AsyncAPI x script de topicos x filas do RabbitMQ.

## Consequencias
A trava ja pegou um problema real na v3: o AWS SDK puxava `commons-logging` (via apache-client);
o build quebrou e a correcao foi usar o `url-connection-client`.
