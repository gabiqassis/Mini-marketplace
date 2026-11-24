# Atalhos (Linux/macOS). No Windows use direto: python scripts/tdd.py <comando>
.PHONY: all unit build up e2e chaos status down clean

all:     ## unit + build + up + e2e
	python3 scripts/tdd.py all

unit:    ## testes unitarios + travas de arquitetura
	python3 scripts/tdd.py unit

build:
	python3 scripts/tdd.py build

up:      ## sobe infra + 9 servicos
	python3 scripts/tdd.py up

e2e:     ## suite E2E (pytest) contra o ambiente no ar
	python3 scripts/tdd.py e2e

chaos:   ## derruba servicos e prova que o resto continua
	python3 scripts/tdd.py chaos

status:
	python3 scripts/tdd.py status

down:
	python3 scripts/tdd.py down

clean:   ## apaga tambem o banco
	python3 scripts/tdd.py clean
