"""Cliente de teste do mini marketplace: fala com o sistema como o mundo real fala.

- Vendedor: publica lotes na fila SQS (AWS SDK boto3, endpoint ElasticMQ) ou via API.
- Comprador: faz pedidos com Idempotency-Key e consulta status/rastreio.
- Transportadora: envia webhooks assinados com HMAC.
- Interno/auditoria: le a trilha de auditoria, notificacoes e o Mailpit (e-mails).
"""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from decimal import Decimal
from typing import Any, Callable

import boto3
import requests

HOST = os.getenv("MARKETPLACE_HOST", "localhost")
URL = {
    "order": f"http://{HOST}:8080",
    "catalog": f"http://{HOST}:8081",
    "inventory": f"http://{HOST}:8082",
    "payment": f"http://{HOST}:8083",
    "invoice": f"http://{HOST}:8084",
    "shipping": f"http://{HOST}:8085",
    "ingestion": f"http://{HOST}:8086",
    "notification": f"http://{HOST}:8087",
    "audit": f"http://{HOST}:8088",
    "mailpit": f"http://{HOST}:8025",
    "sqs": f"http://{HOST}:9324",
}
WEBHOOK_SECRET = os.getenv("CARRIER_WEBHOOK_SECRET", "dev-only-carrier-secret-change-me")
VALID_CNPJ = "11.222.333/0001-81"
BLOCKED_CNPJ = "11.444.777/0001-61"  # CNPJ valido, mas "nao habilitado" na SEFAZ simulada (Lambda)
TIMEOUT = float(os.getenv("E2E_TIMEOUT", "60"))
ADDRESS = {"zipCode": "13010-100", "street": "Rua Barao de Jaguara", "number": "1000", "city": "Campinas", "state": "SP"}


def run_id() -> str:
    """Sufixo unico por teste (so digitos: valido em sellerId minusculo e em SKU maiusculo)."""
    return str(uuid.uuid4().int)[:9]


def wait_until(probe: Callable[[], Any], what: str, timeout: float = TIMEOUT, every: float = 0.5) -> Any:
    """Sistema assincrono: espera a condicao ficar verdadeira (eventual consistency)."""
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            last = probe()
            if last:
                return last
        except requests.RequestException as e:  # servico reiniciando
            last = e
        time.sleep(every)
    raise AssertionError(f"timeout esperando: {what} (ultimo valor: {last!r})")


# ------------------------------------------------------------------ vendedor
@dataclass
class Seller:
    seller_id: str
    cnpj: str = VALID_CNPJ
    email: str = ""
    sequence: int = 0
    skus: list[str] = field(default_factory=list)

    def __post_init__(self):
        self.email = self.email or f"{self.seller_id}@vendedor.test"

    def batch(self, items: list[dict], batch_id: str | None = None) -> dict:
        body_items = []
        for it in items:
            self.sequence += 1
            body_items.append({"sequence": self.sequence, **it})
        return {
            "batchId": batch_id or f"lote-{run_id()}",
            "sellerId": self.seller_id,
            "seller": {"legalName": f"{self.seller_id} LTDA", "cnpj": self.cnpj, "email": self.email},
            "items": body_items,
        }


def sqs():
    return boto3.client("sqs", endpoint_url=URL["sqs"], region_name="us-east-1",
                        aws_access_key_id="local", aws_secret_access_key="local")


def publish_batch_sqs(batch: dict | str) -> str:
    client = sqs()
    queue = client.get_queue_url(QueueName="seller-batches")["QueueUrl"]
    body = batch if isinstance(batch, str) else json.dumps(batch)
    return client.send_message(QueueUrl=queue, MessageBody=body)["MessageId"]


def dlq_bodies() -> list[str]:
    client = sqs()
    queue = client.get_queue_url(QueueName="seller-batches-dlq")["QueueUrl"]
    out = []
    for _ in range(5):
        msgs = client.receive_message(QueueUrl=queue, MaxNumberOfMessages=10, WaitTimeSeconds=1,
                                      VisibilityTimeout=1).get("Messages", [])
        out += [m["Body"] for m in msgs]
        if not msgs:
            break
    return out


def batch_report(seller_id: str, batch_id: str) -> dict | None:
    r = requests.get(f"{URL['ingestion']}/sellers/{seller_id}/batches/{batch_id}", timeout=5)
    return r.json() if r.status_code == 200 else None


def item(sku: str) -> dict | None:
    r = requests.get(f"{URL['catalog']}/items/{sku}", timeout=5)
    return r.json() if r.status_code == 200 else None


def stock(sku: str) -> dict | None:
    r = requests.get(f"{URL['inventory']}/stock/{sku}", timeout=5)
    return r.json() if r.status_code == 200 else None


def publish_and_wait(seller: Seller, items: list[dict]) -> dict:
    """Publica um lote via SQS e espera os itens chegarem ao catalogo, estoque e copia de preco do pedido."""
    batch = seller.batch(items)
    publish_batch_sqs(batch)
    for it in batch["items"]:
        wait_until(lambda: (s := stock(it["sku"])) and s["onHand"] >= 0 and item(it["sku"]), f"item {it['sku']} no catalogo/estoque")
    # a copia de preco do order-service tambem e assincrona: espera aceitar um pedido de "sondagem"? Nao:
    # aguarda o evento ser consumido verificando a trilha de auditoria do sku.
    for it in batch["items"]:
        wait_until(lambda: any(s["type"] == "CatalogItemUpserted" for s in audit_item(it["sku"])), f"auditoria do {it['sku']}")
    time.sleep(1.0)
    return batch


# ------------------------------------------------------------------ comprador
def place_order(buyer: str, lines: list[tuple[str, int]], key: str | None = None, email: str | None = None,
                address: dict | None = None) -> requests.Response:
    return requests.post(
        f"{URL['order']}/orders",
        headers={"Idempotency-Key": key or f"key-{run_id()}"},
        json={"buyerId": buyer, "buyerEmail": email or f"{buyer}@comprador.test", "shippingAddress": address or ADDRESS,
              "items": [{"sku": s, "quantity": q} for s, q in lines]},
        timeout=10)


def order(order_id: str) -> dict:
    return requests.get(f"{URL['order']}/orders/{order_id}", timeout=5).json()


def wait_status(order_id: str, *statuses: str, timeout: float = TIMEOUT) -> dict:
    def probe():
        o = order(order_id)
        return o if o["status"] in statuses else None
    return wait_until(probe, f"pedido {order_id} em {statuses}", timeout)


def payment(order_id: str) -> dict | None:
    r = requests.get(f"{URL['payment']}/payments/by-order/{order_id}", timeout=5)
    return r.json() if r.status_code == 200 else None


def invoices(order_id: str) -> list[dict]:
    return requests.get(f"{URL['invoice']}/invoices", params={"orderId": order_id}, timeout=5).json()


def tracking(order_id: str) -> dict | None:
    r = requests.get(f"{URL['shipping']}/shipments/{order_id}", timeout=5)
    return r.json() if r.status_code == 200 else None


def tracking_code(order_id: str, seller_id: str) -> str:
    def probe():
        t = tracking(order_id)
        if not t:
            return None
        for s in t["shipments"]:
            if s["shipment"]["sellerId"] == seller_id and s["shipment"]["trackingCode"]:
                return s["shipment"]["trackingCode"]
        return None
    return wait_until(probe, f"etiqueta do pedido {order_id} / {seller_id}")


# ------------------------------------------------------------------ transportadora
def carrier_webhook(tracking: str, status: str, location: str = "Campinas/SP", event_id: str | None = None,
                    secret: str = WEBHOOK_SECRET, timestamp: int | None = None) -> requests.Response:
    body = json.dumps({"eventId": event_id or f"evt-{uuid.uuid4()}", "trackingCode": tracking, "status": status,
                       "location": location, "description": f"Transportadora: {status}",
                       "occurredAt": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")})
    ts = str(timestamp or int(time.time()))
    sig = hmac.new(secret.encode(), f"{ts}.{body}".encode(), hashlib.sha256).hexdigest()
    return requests.post(f"{URL['shipping']}/carrier/webhooks", data=body, timeout=10,
                         headers={"Content-Type": "application/json", "X-Carrier-Timestamp": ts, "X-Carrier-Signature": sig})


def deliver(order_id: str, seller_id: str) -> str:
    code = tracking_code(order_id, seller_id)
    for st, loc in [("PICKED_UP", "Campinas/SP"), ("IN_TRANSIT", "Jundiai/SP"), ("IN_TRANSIT", "Sao Paulo/SP"),
                    ("OUT_FOR_DELIVERY", "Sao Paulo/SP"), ("DELIVERED", "Sao Paulo/SP")]:
        assert carrier_webhook(code, st, loc).status_code in (200, 202)
    return code


# ------------------------------------------------------------------ interno / auditoria
def audit_order(order_id: str) -> dict | None:
    r = requests.get(f"{URL['audit']}/audit/orders/{order_id}", timeout=5)
    return r.json() if r.status_code == 200 else None


def audit_item(sku: str) -> list[dict]:
    return requests.get(f"{URL['audit']}/audit/items/{sku}", timeout=5).json()


def notifications(order_id: str) -> list[dict]:
    return requests.get(f"{URL['notification']}/notifications", params={"orderId": order_id}, timeout=5).json()


def emails_to(address: str) -> list[dict]:
    r = requests.get(f"{URL['mailpit']}/api/v1/search", params={"query": f'to:"{address}"', "limit": 200}, timeout=5)
    return r.json().get("messages", [])


def wait_email(address: str, subject_contains: str) -> dict:
    return wait_until(lambda: next((m for m in emails_to(address) if subject_contains in m["Subject"]), None),
                      f"e-mail '{subject_contains}' para {address}")


def money(v) -> Decimal:
    return Decimal(str(v)).quantize(Decimal("0.01"))
