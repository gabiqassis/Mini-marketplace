"""Idempotencia (API, mensagens, webhooks) e seguranca da borda (assinatura, replay, rate limit)."""
import time

import requests
from conftest import order_ready

import marketplace as mp


def test_same_idempotency_key_returns_same_order_without_charging_twice(seller, run):
    sku = f"IDEM-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Mochila", "price": 120, "stockQty": 10}])
    key = f"checkout-{run}"

    first = order_ready(f"buyer-{run}", [(sku, 1)], key=key)
    retry = mp.place_order(f"buyer-{run}", [(sku, 1)], key=key)   # clique duplo / retry do app

    assert first.status_code == 201 and retry.status_code == 200
    assert retry.json()["id"] == first.json()["id"]
    mp.wait_status(first.json()["id"], "INVOICED", "IN_DELIVERY", "DELIVERED")
    assert mp.stock(sku)["onHand"] == 9, "estoque baixado uma unica vez"
    pay = mp.wait_until(lambda: (p := mp.payment(first.json()["id"])) and p["payment"]["status"] == "CAPTURED" and p, "captura")
    assert [e["type"] for e in pay["ledger"]] == ["AUTHORIZATION", "CAPTURE"], "uma autorizacao e uma captura, nunca duas"


def test_same_key_with_different_body_is_rejected(seller, run):
    sku = f"IDEM2-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Garrafa", "price": 30, "stockQty": 10}])
    key = f"checkout-{run}"
    assert order_ready(f"buyer-{run}", [(sku, 1)], key=key).status_code == 201
    other = mp.place_order(f"buyer-{run}", [(sku, 3)], key=key)
    assert other.status_code == 422 and "Idempotency-Key" in other.text


def test_api_validation_errors(run):
    no_key = requests.post(f"{mp.URL['order']}/orders", timeout=5, json={
        "buyerId": "b", "buyerEmail": "b@x.com", "shippingAddress": mp.ADDRESS, "items": [{"sku": "X", "quantity": 1}]})
    assert no_key.status_code == 400
    bad_zip = mp.place_order(f"buyer-{run}", [("X", 1)], address={**mp.ADDRESS, "zipCode": "123"})
    assert bad_zip.status_code == 400 and "CEP" in bad_zip.text
    unknown = mp.place_order(f"buyer-{run}", [(f"NAOEXISTE-{run}", 1)])
    assert unknown.status_code == 422


def test_duplicate_carrier_webhook_creates_a_single_tracking_event(seller, run):
    sku = f"WH-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Relogio", "price": 200, "stockQty": 3}])
    o = order_ready(f"buyer-{run}", [(sku, 1)]).json()
    code = mp.tracking_code(o["id"], seller.seller_id)

    event_id = f"evt-dup-{run}"
    first = mp.carrier_webhook(code, "PICKED_UP", event_id=event_id)
    again = mp.carrier_webhook(code, "PICKED_UP", event_id=event_id)   # transportadora reenviou

    assert first.status_code == 202 and again.status_code == 200 and again.json()["result"] == "DUPLICATE"
    timeline = mp.tracking(o["id"])["shipments"][0]["timeline"]
    assert [e["status"] for e in timeline].count("PICKED_UP") == 1


def test_webhook_with_bad_signature_or_old_timestamp_is_refused(seller, run):
    sku = f"SIG-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Oculos", "price": 99, "stockQty": 3}])
    o = order_ready(f"buyer-{run}", [(sku, 1)]).json()
    code = mp.tracking_code(o["id"], seller.seller_id)

    forged = mp.carrier_webhook(code, "DELIVERED", secret="segredo-do-atacante-123456")
    replay = mp.carrier_webhook(code, "DELIVERED", timestamp=int(time.time()) - 3600)

    assert forged.status_code == 401 and replay.status_code == 401
    assert mp.order(o["id"])["status"] != "DELIVERED"


def test_duplicate_invoice_event_never_creates_a_second_label(seller, run):
    """Envio duplicado: a etiqueta e unica por (pedido, vendedor), mesmo com reentrega de mensagens."""
    sku = f"LBL-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Teclado", "price": 180, "stockQty": 3}])
    o = order_ready(f"buyer-{run}", [(sku, 1)]).json()
    code = mp.tracking_code(o["id"], seller.seller_id)
    time.sleep(2)
    t = mp.tracking(o["id"])
    assert len(t["shipments"]) == 1 and t["shipments"][0]["shipment"]["trackingCode"] == code
    created = [e for e in t["shipments"][0]["timeline"] if e["status"] == "CREATED"]
    assert len(created) == 1


def test_burst_from_same_buyer_is_rate_limited(seller, run):
    sku = f"RL-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Adesivo", "price": 5, "stockQty": 1000}])
    order_ready(f"bot-{run}", [(sku, 1)])
    codes = [mp.place_order(f"bot-{run}", [(sku, 1)]).status_code for _ in range(40)]
    assert 429 in codes, "rajada acima de 30/min deve receber 429"
