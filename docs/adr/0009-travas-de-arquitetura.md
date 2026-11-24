# ADR-0009: Travas de arquitetura (o build quebra se alguem violar)

**Status:** aceito (revisado na v3)

## Decisao
**ArchUnit** (`libs/architecture-rules`, roda em cada servico):
1. domain nao conhece Spring, Kafka, Jackson, jakarta, javax nem outras camadas
2. application nao conhece api nem infra
3. api nao conhece infra; infra nao conhece api
4. SQL (`JdbcClient`) so em infra
5. `@KafkaListener` so em infra
6. `@RabbitListener` so em infra
7. AWS SDK so em infra
8. sem injecao por campo (`@Autowired` em atributo)
9. sem ciclos entre camadas
10. sem `System.out` / `java.util.logging`

**Maven Enforcer**: Java 21+, servico nao depende de servico, `commons-logging` banido.
**Banco**: CHECK, UNIQUE, FK e triggers append-only (a regra vale mesmo se o codigo errar).
**SDD**: `TopicsMatchSpecTest` compara codigo x AsyncAPI x script de topicos x filas do RabbitMQ.

## Consequencias
A trava ja pegou um problema real na v3: o AWS SDK puxava `commons-logging` (via apache-client);
o build quebrou e a correcao foi usar o `url-connection-client`.
