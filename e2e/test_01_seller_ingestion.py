"""Vendedor -> AWS SQS -> ingestion -> Kafka -> catalogo + estoque."""
import json

import marketplace as mp


def test_seller_batch_via_sqs_reaches_catalog_and_stock(seller, run):
    batch = mp.publish_and_wait(seller, [
        {"sku": f"CAM-{run}", "title": "Camiseta Algodao", "price": 59.90, "stockQty": 10},
        {"sku": f"CAN-{run}", "title": "Caneca", "price": 25.00, "stockQty": 3},
    ])

    assert mp.item(f"CAM-{run}")["price"] == 59.90
    assert mp.stock(f"CAM-{run}")["onHand"] == 10
    assert mp.stock(f"CAN-{run}")["onHand"] == 3
    report = mp.wait_until(lambda: mp.batch_report(seller.seller_id, batch["batchId"]), "relatorio do lote")
    assert report["source"] == "SQS" and report["accepted"] == 2 and report["rejected"] == 0


def test_resent_batch_is_idempotent(seller, run):
    batch = seller.batch([{"sku": f"DUP-{run}", "title": "Bone", "price": 30, "stockQty": 7}])
    mp.publish_batch_sqs(batch)
    mp.publish_batch_sqs(batch)  # SQS entrega at-least-once; vendedor tambem reenvia
    mp.wait_until(lambda: mp.stock(f"DUP-{run}"), "estoque do bone")

    published = [s for s in mp.audit_item(f"DUP-{run}") if s["type"] == "SellerItemPublished"]
    assert len(published) == 1, "lote repetido nao pode publicar o item duas vezes"


def test_invalid_items_are_rejected_individually_with_reason(seller, run):
    batch = seller.batch([
        {"sku": f"OK-{run}", "title": "Valido", "price": 10, "stockQty": 1},
        {"sku": f"NEG-{run}", "title": "Preco negativo", "price": -5, "stockQty": 1},
        {"sku": "minusculo", "title": "Sku invalido", "price": 5, "stockQty": 1},
    ])
    mp.publish_batch_sqs(batch)
    report = mp.wait_until(lambda: mp.batch_report(seller.seller_id, batch["batchId"]), "relatorio do lote")

    assert report["accepted"] == 1 and report["rejected"] == 2
    reasons = {i["sku"]: i["reason"] for i in report["items"] if i["status"] == "REJECTED"}
    assert "preco" in reasons[f"NEG-{run}"]
    assert mp.wait_until(lambda: mp.stock(f"OK-{run}"), "item valido entrou")
    assert mp.stock(f"NEG-{run}") is None


def test_broken_batch_goes_to_sqs_dead_letter_queue(run):
    broken = json.dumps({"batchId": f"quebrado-{run}", "sellerId": "Vendedor Invalido!", "items": []})
    mp.publish_batch_sqs(broken)
    mp.wait_until(lambda: any(f"quebrado-{run}" in b for b in mp.dlq_bodies()), "lote invalido na DLQ do SQS")


def test_out_of_order_update_never_overwrites_newer_data(seller, run):
    sku = f"ORD-{run}"
    newer = {"batchId": f"novo-{run}", "sellerId": seller.seller_id,
             "seller": {"legalName": "X", "cnpj": mp.VALID_CNPJ, "email": seller.email},
             "items": [{"sku": sku, "title": "Versao 2", "price": 20, "stockQty": 5, "sequence": 2}]}
    older = {**newer, "batchId": f"velho-{run}",
             "items": [{"sku": sku, "title": "Versao 1", "price": 10, "stockQty": 99, "sequence": 1}]}
    mp.publish_batch_sqs(newer)
    mp.wait_until(lambda: (i := mp.item(sku)) and i["title"] == "Versao 2", "versao 2")
    mp.publish_batch_sqs(older)  # chega atrasada
    mp.wait_until(lambda: mp.batch_report(seller.seller_id, f"velho-{run}"), "lote velho processado")

    assert mp.item(sku)["title"] == "Versao 2"
    assert mp.stock(sku)["onHand"] == 5


def test_seller_can_also_use_the_http_api(seller, run):
    import requests
    batch = seller.batch([{"sku": f"API-{run}", "title": "Via API", "price": 12.5, "stockQty": 2}])
    r = requests.post(f"{mp.URL['ingestion']}/sellers/{seller.seller_id}/batches", json=batch, timeout=10)
    assert r.status_code == 202
    again = requests.post(f"{mp.URL['ingestion']}/sellers/{seller.seller_id}/batches", json=batch, timeout=10)
    assert again.status_code == 200, "reenvio pela API devolve o mesmo relatorio"
    bad = requests.post(f"{mp.URL['ingestion']}/sellers/{seller.seller_id}/batches",
                        json={**batch, "batchId": f"x-{run}", "seller": {**batch["seller"], "cnpj": "123"}}, timeout=10)
    assert bad.status_code == 400 and "CNPJ" in bad.text
