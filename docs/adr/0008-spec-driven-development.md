# ADR-0008: Spec-Driven Development (SDD)

**Status:** aceita

## Contexto
Com vários serviços conversando por eventos, o maior risco é o contrato divergir entre quem manda e quem recebe sem ninguém perceber.

## Decisão
O contrato é escrito **antes** do código e fica em [`docs/contracts/`](../contracts):

1. [`asyncapi.yaml`](../contracts/asyncapi.yaml): eventos Kafka (tópicos, payloads, produtores e consumidores).
2. [`rabbitmq.md`](../contracts/rabbitmq.md): comandos e filas (retry, parking).
3. [`openapi.yaml`](../contracts/openapi.yaml): REST de cada serviço.
4. Regra de mudança: alterou comportamento ou contrato? **Mude o contrato primeiro**, depois o teste e o código.

Para o contrato não virar documento esquecido, há um teste que o cobra: `TopicsMatchContractsTest` quebra o build se os tópicos do código divergirem do AsyncAPI, do script que cria os tópicos ou das filas do RabbitMQ.

## Alternativas consideradas
- **Code-first (contrato gerado do código):** o contrato passa a ser consequência, e mudança acidental vira "contrato novo".
- **Só TDD:** testa comportamento, mas não deixa o contrato entre serviços escrito em um lugar.

## Consequências
- ➕ Contrato revisável antes de existir código; cada requisito aponta para o teste que o prova.
- ➖ Disciplina de manter a spec. Hoje só os nomes de tópico são cobrados automaticamente; validar **payloads** contra o AsyncAPI (teste de contrato) é o próximo passo.
