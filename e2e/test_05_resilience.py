"""Caos controlado: derruba pecas do sistema e prova que o resto continua.
Rode com:  python -m pytest -m chaos   (usa docker compose stop/start)."""
import subprocess
import time
from pathlib import Path

import pytest
import requests
from conftest import order_ready

import marketplace as mp

ROOT = Path(__file__).resolve().parents[1]


def compose(*args):
    subprocess.run(["docker", "compose", *args], cwd=ROOT, check=True, capture_output=True, timeout=180)


def wait_healthy(service_port: str):
    mp.wait_until(lambda: requests.get(f"{mp.URL[service_port]}/actuator/health", timeout=2).json()["status"] == "UP",
                  f"{service_port} de volta", timeout=180, every=2)


@pytest.mark.chaos
def test_notification_down_does_not_stop_sales_and_emails_arrive_later(seller, run):
    sku = f"CH1-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Violao", "price": 700, "stockQty": 2}])
    buyer = f"buyer-{run}"
    compose("stop", "notification-service")
    try:
        o = order_ready(buyer, [(sku, 1)]).json()
        mp.deliver(o["id"], seller.seller_id)
        mp.wait_status(o["id"], "DELIVERED")      # vendeu, faturou, capturou e entregou sem notificacao
        assert mp.emails_to(seller.email) == []
    finally:
        compose("start", "notification-service")
    wait_healthy("notification")
    mp.wait_until(lambda: any("Voce vendeu" in m["Subject"] for m in mp.emails_to(seller.email)),
                  "e-mail do vendedor entregue depois que o servico voltou", timeout=120)
    mp.wait_email(f"{buyer}@comprador.test", "entregue")


@pytest.mark.chaos
def test_redis_down_degrades_gracefully(seller, run):
    sku = f"CH2-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Tapete", "price": 90, "stockQty": 5}])
    compose("stop", "redis")
    try:
        assert mp.item(sku)["title"] == "Tapete"                       # catalogo le do banco
        o = order_ready(f"buyer-{run}", [(sku, 1)]).json()             # rate limit fail-open
        mp.wait_status(o["id"], "INVOICED", "IN_DELIVERY")
        assert mp.tracking(o["id"]) is not None                        # rastreio sem cache
    finally:
        compose("start", "redis")


@pytest.mark.chaos
def test_lambda_down_retries_then_issues_invoice_when_it_comes_back(seller, run):
    sku = f"CH3-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Panela", "price": 150, "stockQty": 5}])
    compose("stop", "nfe-lambda")
    try:
        o = order_ready(f"buyer-{run}", [(sku, 1)]).json()
        mp.wait_status(o["id"], "CONFIRMED")
        time.sleep(3)
        assert mp.order(o["id"])["status"] == "CONFIRMED", "sem SEFAZ a nota espera (retry com backoff no RabbitMQ)"
    finally:
        compose("start", "nfe-lambda")
    mp.wait_status(o["id"], "INVOICED", "IN_DELIVERY", timeout=120)


@pytest.mark.chaos
def test_kafka_consumer_down_messages_wait_and_are_processed_on_return(seller, run):
    """Inventario fora: pedidos ficam PENDING (nao se perdem); voltou, a SAGA continua do ponto onde parou."""
    sku = f"CH4-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Mesa", "price": 400, "stockQty": 3}])
    compose("stop", "inventory-service")
    try:
        o = order_ready(f"buyer-{run}", [(sku, 1)]).json()
        time.sleep(3)
        assert mp.order(o["id"])["status"] == "PENDING"
    finally:
        compose("start", "inventory-service")
    mp.wait_status(o["id"], "INVOICED", "IN_DELIVERY", timeout=180)
