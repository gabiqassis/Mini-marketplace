import time

import pytest
import requests

import marketplace as mp


@pytest.fixture(scope="session", autouse=True)
def system_is_up():
    """Antes de qualquer teste: todos os servicos respondendo /actuator/health = UP."""
    services = ["order", "catalog", "inventory", "payment", "invoice", "shipping", "ingestion", "notification", "audit"]

    def all_up():
        for s in services:
            r = requests.get(f"{mp.URL[s]}/actuator/health", timeout=3)
            if r.status_code != 200 or r.json().get("status") != "UP":
                return False
        return True

    mp.wait_until(all_up, "todos os servicos UP", timeout=240, every=2)


@pytest.fixture
def run():
    return mp.run_id()


@pytest.fixture
def seller(run):
    return mp.Seller(seller_id=f"loja-{run}")


def order_ready(buyer, lines, **kw):
    """Copia de preco no order-service e assincrona: tenta por ate 15 s enquanto o produto for desconhecido."""
    deadline = time.time() + 15
    while True:
        r = mp.place_order(buyer, lines, **kw)
        if r.status_code != 422 or "desconhecido" not in r.text or time.time() > deadline:
            return r
        time.sleep(0.5)
