"""Tratativas: estoque acabou, corrida pela ultima unidade, pagamento recusado/sem resposta,
nota rejeitada, extravio, devolucao e evento fora de ordem."""
import concurrent.futures as cf

from conftest import order_ready

import marketplace as mp


def test_buying_more_than_stock_cancels_and_explains_to_buyer(seller, run):
    sku = f"ESG-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Console", "price": 3000, "stockQty": 1}])
    buyer = f"buyer-{run}"

    o = order_ready(buyer, [(sku, 2)]).json()

    final = mp.wait_status(o["id"], "CANCELLED")
    assert final["reason"].startswith("INSUFFICIENT_STOCK")
    assert mp.stock(sku)["reserved"] == 0
    assert mp.payment(o["id"]) is None, "nada foi cobrado"
    mail = mp.wait_email(f"{buyer}@comprador.test", "cancelado")
    assert mail["Subject"].startswith("Pedido")


def test_race_for_the_last_unit_never_oversells(seller, run):
    sku = f"ULT-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Edicao limitada", "price": 500, "stockQty": 1}])
    def buy(i):
        return order_ready(f"fila-{run}-{i}", [(sku, 1)]).json()

    with cf.ThreadPoolExecutor(max_workers=8) as pool:
        orders = list(pool.map(buy, range(8)))
    finals = [mp.wait_status(o["id"], "CANCELLED", "INVOICED", "IN_DELIVERY") for o in orders]

    winners = [f for f in finals if f["status"] != "CANCELLED"]
    assert len(winners) == 1, f"exatamente 1 comprador leva a ultima unidade, foram {len(winners)}"
    s = mp.stock(sku)
    assert s["onHand"] == 0 and s["reserved"] == 0


def test_declined_payment_releases_the_reserved_stock(seller, run):
    sku = f"CAR-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Notebook gamer", "price": 12000, "stockQty": 2}])

    o = order_ready(f"buyer-{run}", [(sku, 1)]).json()   # acima do limite de 10.000 do adquirente simulado

    final = mp.wait_status(o["id"], "CANCELLED")
    assert final["reason"] == "PAYMENT_LIMIT_EXCEEDED"
    assert mp.stock(sku) == {"sku": sku, "onHand": 2, "reserved": 0, "available": 2}
    assert mp.payment(o["id"])["payment"]["status"] == "DECLINED"


def test_gateway_timeout_is_unknown_state_resolved_by_reconciliation(seller, run):
    """Timeout NAO e recusa: o adquirente aprovou mas respondeu tarde. A reconciliacao consulta pela
    mesma chave de idempotencia e confirma o pedido, sem cobrar duas vezes."""
    sku = f"TMO-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Cadeira", "price": 800, "stockQty": 2}])

    o = order_ready(f"buyer-timeout-{run}", [(sku, 1)]).json()

    mp.wait_until(lambda: (p := mp.payment(o["id"])) and p["payment"]["status"] == "AUTHORIZING", "estado desconhecido")
    mp.wait_status(o["id"], "INVOICED", "IN_DELIVERY", timeout=90)
    p = mp.payment(o["id"])
    assert [e["type"] for e in p["ledger"]].count("AUTHORIZATION") == 1


def test_rejected_invoice_puts_order_on_hold_and_warns_seller(run):
    irregular = mp.Seller(seller_id=f"irregular-{run}", cnpj=mp.BLOCKED_CNPJ)
    sku = f"NFR-{run}"
    mp.publish_and_wait(irregular, [{"sku": sku, "title": "Bicicleta", "price": 900, "stockQty": 2}])

    o = order_ready(f"buyer-{run}", [(sku, 1)]).json()

    final = mp.wait_status(o["id"], "ON_HOLD")
    assert final["reason"] == "INVOICE_REJECTED_203"
    assert mp.invoices(o["id"])[0]["status"] == "REJECTED"
    assert mp.payment(o["id"])["payment"]["status"] == "AUTHORIZED", "sem nota, nao captura"
    assert mp.tracking(o["id"])["shipments"][0]["shipment"]["status"] == "AWAITING_INVOICE", "sem nota, sem etiqueta"
    mp.wait_email(irregular.email, "ACAO NECESSARIA")


def test_lost_shipment_refunds_the_buyer(seller, run):
    sku = f"EXT-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Camera", "price": 1500, "stockQty": 2}])
    buyer = f"buyer-{run}"
    o = order_ready(buyer, [(sku, 1)]).json()
    code = mp.tracking_code(o["id"], seller.seller_id)
    mp.wait_until(lambda: mp.payment(o["id"])["payment"]["status"] == "CAPTURED", "captura")

    mp.carrier_webhook(code, "PICKED_UP")
    mp.carrier_webhook(code, "IN_TRANSIT", "Registro/SP")
    mp.carrier_webhook(code, "LOST", "Registro/SP")

    final = mp.wait_status(o["id"], "REFUNDED")
    assert final["sellers"][0]["problem"] == "LOST"
    p = mp.payment(o["id"])
    assert p["payment"]["status"] == "REFUNDED" and mp.money(p["payment"]["refundedAmount"]) == mp.money(1500)
    assert mp.stock(sku)["onHand"] == 1, "extravio nao devolve estoque (a mercadoria sumiu)"
    mp.wait_email(f"{buyer}@comprador.test", "Problema com o pedido")


def test_returned_shipment_refunds_and_restocks(seller, run):
    sku = f"DEV-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Jaqueta", "price": 350, "stockQty": 3}])
    o = order_ready(f"buyer-{run}", [(sku, 1)]).json()
    code = mp.tracking_code(o["id"], seller.seller_id)
    mp.wait_until(lambda: mp.payment(o["id"])["payment"]["status"] == "CAPTURED", "captura")

    for st in ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "RETURNED"]:
        assert mp.carrier_webhook(code, st, "Sao Paulo/SP").status_code == 202, st

    mp.wait_status(o["id"], "REFUNDED")
    mp.wait_until(lambda: mp.stock(sku)["onHand"] == 3, "estoque reposto apos devolucao")
    mp.wait_email(seller.email, "Devolucao")


def test_out_of_order_carrier_event_is_ignored_but_kept_for_audit(seller, run):
    sku = f"OOO-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Perfume", "price": 250, "stockQty": 2}])
    o = order_ready(f"buyer-{run}", [(sku, 1)]).json()
    code = mp.deliver(o["id"], seller.seller_id)
    mp.wait_status(o["id"], "DELIVERED")

    late = mp.carrier_webhook(code, "IN_TRANSIT", "Atrasado/SP")   # evento velho chegando depois

    assert late.status_code == 202 and late.json()["result"] == "IGNORED"
    assert mp.order(o["id"])["status"] == "DELIVERED"
    t = mp.tracking(o["id"])["shipments"][0]
    assert t["shipment"]["status"] == "DELIVERED"
    assert any(e["location"] == "Atrasado/SP" and e["accepted"] is False for e in t["timeline"])
