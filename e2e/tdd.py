#!/usr/bin/env python3
"""
Orquestrador de testes do mini marketplace (Windows, Linux e macOS).

Ciclo TDD:
  vermelho -> verde -> refatorar, em duas velocidades:
  1) unit   : testes de dominio/caso de uso + travas de arquitetura (segundos, sem Docker)
  2) e2e    : o sistema inteiro no Docker, falando como vendedor, comprador e transportadora

Uso:
  python e2e/tdd.py all            unit + build + up + e2e   (o "tudo verde" da banca)
  python e2e/tdd.py all --chaos    idem + testes de caos (derruba containers)
  python e2e/tdd.py unit [-m order-service]   so testes unitarios (de um modulo, se quiser)
  python e2e/tdd.py build          gera os jars (sem testes)
  python e2e/tdd.py up             sobe a infra + 9 servicos e espera ficarem saudaveis
  python e2e/tdd.py e2e [-k nome]  roda a suite E2E (pytest) contra o ambiente no ar
  python e2e/tdd.py chaos          so os testes de caos
  python e2e/tdd.py status         saude de cada servico
  python e2e/tdd.py logs <servico> logs JSON de um servico (ex.: order-service)
  python e2e/tdd.py down | clean   para tudo | para e apaga o banco

Requisitos: Docker (Docker Desktop no Windows) e Python 3.10+.
Maven/Java 21 sao opcionais: sem eles, o build roda dentro de um container maven.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
import venv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
E2E = ROOT / "e2e"
VENV = ROOT / ".venv-e2e"
IS_WINDOWS = os.name == "nt"
SERVICES = {
    "order-service": 8080, "catalog-service": 8081, "inventory-service": 8082, "payment-service": 8083,
    "invoice-service": 8084, "shipping-service": 8085, "ingestion-service": 8086,
    "notification-service": 8087, "audit-service": 8088,
}
GREEN, RED, YELLOW, BOLD, RESET = ("\033[32m", "\033[31m", "\033[33m", "\033[1m", "\033[0m")
if IS_WINDOWS:
    os.system("")  # habilita cores ANSI no terminal do Windows


def say(msg: str, color: str = BOLD) -> None:
    print(f"{color}==> {msg}{RESET}", flush=True)


def run(cmd: list[str], cwd: Path = ROOT, check: bool = True, env: dict | None = None) -> int:
    print(f"$ {' '.join(cmd)}", flush=True)
    result = subprocess.run(cmd, cwd=cwd, env={**os.environ, **(env or {})})
    if check and result.returncode != 0:
        say(f"falhou: {' '.join(cmd[:3])}...", RED)
        sys.exit(result.returncode)
    return result.returncode


# ------------------------------------------------------------------ Maven
def maven(goals: list[str]) -> None:
    mvn = shutil.which("mvn.cmd" if IS_WINDOWS else "mvn") or shutil.which("mvn")
    if mvn:
        run([mvn, "-B", *goals])
        return
    say("Maven nao encontrado: usando o container maven:3.9-eclipse-temurin-21", YELLOW)
    run(["docker", "run", "--rm", "-v", f"{ROOT}:/src", "-v", "mini-marketplace-m2:/root/.m2", "-w", "/src",
         "maven:3.9-eclipse-temurin-21", "mvn", "-B", *goals])


def cmd_unit(args) -> None:
    say("TESTES UNITARIOS + travas de arquitetura (ArchUnit, Maven Enforcer, contrato x spec)")
    goals = ["test"] if not args.module else ["test", "-pl", f"services/{args.module}", "-am"]
    maven(goals)
    summarize_unit()


def summarize_unit() -> None:
    import re
    total = failed = 0
    for report in ROOT.glob("**/target/surefire-reports/TEST-*.xml"):
        head = report.read_text(encoding="utf-8", errors="ignore")[:3000]
        t = re.search(r'tests="(\d+)"', head)
        f = re.search(r'failures="(\d+)"', head)
        e = re.search(r'errors="(\d+)"', head)
        total += int(t.group(1)) if t else 0
        failed += (int(f.group(1)) if f else 0) + (int(e.group(1)) if e else 0)
    color = GREEN if failed == 0 else RED
    say(f"unitarios: {total} testes, {failed} falha(s)", color)


def cmd_build(_args=None) -> None:
    say("BUILD dos jars (sem testes)")
    maven(["package", "-DskipTests", "-q"])


# ------------------------------------------------------------------ Docker
def compose(*args: str, check: bool = True) -> int:
    return run(["docker", "compose", *args], check=check)


def health(port: int) -> str:
    try:
        with urllib.request.urlopen(f"http://localhost:{port}/actuator/health", timeout=3) as r:
            return json.loads(r.read()).get("status", "?")
    except Exception:  # noqa: BLE001 - qualquer falha = ainda nao esta de pe
        return "DOWN"


def wait_healthy(timeout: int = 300) -> None:
    say("aguardando os 9 servicos ficarem UP (primeira vez pode levar alguns minutos)")
    deadline = time.time() + timeout
    while time.time() < deadline:
        states = {s: health(p) for s, p in SERVICES.items()}
        if all(v == "UP" for v in states.values()):
            say("todos os servicos UP", GREEN)
            return
        time.sleep(3)
    for s, v in states.items():
        print(f"  {s:22s} {v}")
    say("servicos nao subiram a tempo: veja 'python e2e/tdd.py logs <servico>'", RED)
    sys.exit(1)


def cmd_up(_args=None) -> None:
    jars = list(ROOT.glob("services/*/target/*-SNAPSHOT.jar"))
    if len(jars) < len(SERVICES) or not list(ROOT.glob("lambdas/nfe-emitter-lambda/target/*-lambda.jar")):
        cmd_build()
    say("SUBINDO ambiente: Postgres, Kafka, RabbitMQ, Redis, SQS (ElasticMQ), Lambda (RIE), Mailpit + 9 servicos")
    compose("up", "-d", "--build")
    wait_healthy()
    print(f"""
  Pedidos        http://localhost:8080/orders
  Rastreio       http://localhost:8085/shipments/<orderId>
  Auditoria      http://localhost:8088/audit/orders/<orderId>
  E-mails        http://localhost:8025          (Mailpit)
  RabbitMQ       http://localhost:15672         (marketplace / marketplace)
  Banco          localhost:5432/marketplace     (observer / observer = leitura de todos os schemas)
""")


def cmd_status(_args=None) -> None:
    for s, p in SERVICES.items():
        st = health(p)
        print(f"  {(GREEN if st == 'UP' else RED)}{s:22s} {st}{RESET}  http://localhost:{p}")


def cmd_logs(args) -> None:
    compose("logs", "--tail", "200", "-f", args.service, check=False)


def cmd_down(_args=None) -> None:
    compose("down")


def cmd_clean(_args=None) -> None:
    compose("down", "-v")


# ------------------------------------------------------------------ E2E (pytest)
def venv_python() -> Path:
    py = VENV / ("Scripts/python.exe" if IS_WINDOWS else "bin/python")
    if not py.exists():
        say("criando ambiente Python isolado para os testes (.venv-e2e)")
        venv.EnvBuilder(with_pip=True).create(VENV)
        run([str(py), "-m", "pip", "install", "-q", "--upgrade", "pip"])
    run([str(py), "-m", "pip", "install", "-q", "-r", str(E2E / "requirements.txt")])
    return py


def pytest(marker: str, extra: list[str]) -> int:
    py = venv_python()
    return run([str(py), "-m", "pytest", "-v", "-m", marker, *extra], cwd=E2E, check=False)


def cmd_e2e(args) -> None:
    say("TESTES E2E: vendedor (SQS) -> SAGA -> NF-e (Lambda) -> envio -> entrega -> auditoria")
    extra = ["-k", args.k] if getattr(args, "k", None) else []
    rc = pytest("not chaos", extra)
    say("E2E verde" if rc == 0 else "E2E com falhas", GREEN if rc == 0 else RED)
    if rc:
        sys.exit(rc)


def cmd_chaos(_args=None) -> None:
    say("TESTES DE CAOS: derrubando servicos e provando que o resto continua")
    rc = pytest("chaos", [])
    say("caos verde" if rc == 0 else "caos com falhas", GREEN if rc == 0 else RED)
    if rc:
        sys.exit(rc)


def cmd_all(args) -> None:
    started = time.time()
    cmd_unit(argparse.Namespace(module=None))
    cmd_build()
    cmd_up()
    cmd_e2e(argparse.Namespace(k=None))
    if args.chaos:
        cmd_chaos()
    say(f"TUDO VERDE em {int(time.time() - started)} s", GREEN)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("unit")
    p.add_argument("-m", "--module", help="ex.: order-service")
    p.set_defaults(fn=cmd_unit)
    sub.add_parser("build").set_defaults(fn=cmd_build)
    sub.add_parser("up").set_defaults(fn=cmd_up)
    p = sub.add_parser("e2e")
    p.add_argument("-k", help="filtro do pytest (nome do teste)")
    p.set_defaults(fn=cmd_e2e)
    sub.add_parser("chaos").set_defaults(fn=cmd_chaos)
    p = sub.add_parser("all")
    p.add_argument("--chaos", action="store_true")
    p.set_defaults(fn=cmd_all)
    sub.add_parser("status").set_defaults(fn=cmd_status)
    p = sub.add_parser("logs")
    p.add_argument("service")
    p.set_defaults(fn=cmd_logs)
    sub.add_parser("down").set_defaults(fn=cmd_down)
    sub.add_parser("clean").set_defaults(fn=cmd_clean)
    args = parser.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
