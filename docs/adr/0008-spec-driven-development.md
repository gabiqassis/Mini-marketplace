# ADR-0008: Spec-Driven Development (SDD)

**Status:** aceita

## Contexto
Com vários serviços conversando por eventos, o maior risco é o contrato divergir entre quem manda e quem recebe sem ninguém perceber.

## Decisão
A pasta [`specs/`](../../specs) é a **fonte da verdade**; o código a implementa:

1. [`constitution.md`](../../specs/constitution.md): princípios inegociáveis (as travas).
2. `specs/00N-*/spec.md`: por feature — requisitos numerados (`RF-`), cenários de aceite *Dado/Quando/Então* e rastreabilidade para código e teste.
3. `specs/contracts/`: **AsyncAPI** (eventos) e **OpenAPI** (REST).
4. Regra de mudança: alterou comportamento ou contrato? **Mude a spec primeiro**, depois o código e o teste.

Para a spec não virar documento esquecido, há um teste que a cobra: `TopicsMatchSpecTest` quebra o build se os tópicos do código divergirem do AsyncAPI ou do script que cria os tópicos.

## Alternativas consideradas
- **Code-first (contrato gerado do código):** o contrato passa a ser consequência, e mudança acidental vira "contrato novo".
- **Só TDD:** testa comportamento, mas não deixa o contrato entre serviços escrito em um lugar.

## Consequências
- ➕ Contrato revisável antes de existir código; cada requisito aponta para o teste que o prova.
- ➖ Disciplina de manter a spec. Hoje só os nomes de tópico são cobrados automaticamente; validar **payloads** contra o AsyncAPI (teste de contrato) é o próximo passo.
