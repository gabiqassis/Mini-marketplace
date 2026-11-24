"""Caminho feliz completo: do item publicado pelo vendedor ate a entrega, com nota, captura,
notificacoes, rastreio e a trilha de auditoria ligando tudo."""
from conftest import order_ready

import marketplace as mp


def test_full_purchase_from_seller_stock_to_delivered(seller, run):
    sku = f"TEN-{run}"
    mp.publish_and_wait(seller, [{"sku": sku, "title": "Tenis Corrida", "price": 299.90, "stockQty": 5}])
    buyer = f"buyer-{run}"

    r = order_ready(buyer, [(sku, 2)])
    assert r.status_code == 201, r.text
    o = r.json()
    assert mp.money(o["total"]) == mp.money("599.80")

    # SAGA: reserva -> autoriza -> confirma (baixa estoque) -> NF-e via Lambda -> captura
    mp.wait_status(o["id"], "INVOICED", "IN_DELIVERY", "DELIVERED")
    assert mp.stock(sku) == {"sku": sku, "onHand": 3, "reserved": 0, "available": 3}
    inv = mp.wait_until(lambda: [i for i in mp.invoices(o["id"]) if i["status"] == "AUTHORIZED"], "NF-e autorizada")[0]
    assert len(inv["accessKey"]) == 44 and inv["number"] >= 1
    pay = mp.wait_until(lambda: (p := mp.payment(o["id"])) and p["payment"]["status"] == "CAPTURED" and p, "captura")
    assert [e["type"] for e in pay["ledger"]] == ["AUTHORIZATION", "CAPTURE"]

    # Transportadora: coleta, checkpoints com local, saiu para entrega, entregue
    code = mp.deliver(o["id"], seller.seller_id)
    final = mp.wait_status(o["id"], "DELIVERED")
    assert final["sellers"][0]["trackingCode"] == code
    assert final["sellers"][0]["invoiceKey"] == inv["accessKey"]

    # Onde esta a encomenda: linha do tempo com locais (cache Redis na segunda leitura)
    t = mp.tracking(o["id"])
    timeline = t["shipments"][0]["timeline"]
    assert [e["status"] for e in timeline] == ["CREATED", "PICKED_UP", "IN_TRANSIT", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"]
    assert "Jundiai/SP" in [e["location"] for e in timeline]
    assert mp.tracking(o["id"])["fromCache"] is True

    # Notificacoes: vendedor soube da venda e da nota; comprador acompanhou ate a entrega
    mp.wait_email(seller.email, "Voce vendeu")
    mp.wait_email(seller.email, "NF-e")
    mp.wait_email(f"{buyer}@comprador.test", "confirmado")
    mp.wait_email(f"{buyer}@comprador.test", "entregue")
    sent = mp.wait_until(lambda: (n := mp.notifications(o["id"])) and all(x["status"] == "SENT" for x in n) and n, "todas enviadas")
    templates = {n["template"] for n in sent}
    assert {"SALE", "ORDER_CONFIRMED", "INVOICE_ISSUED", "SHIPMENT_CREATED", "SHIPMENT_DELIVERED", "SELLER_DELIVERED"} <= templates

    # Auditoria ponta a ponta: o pedido E o lado do vendedor (lote que colocou o item no estoque)
    trail = mp.wait_until(lambda: (a := mp.audit_order(o["id"])) and a["currentStatus"] == "DELIVERED" and a, "trilha completa")
    types = [s["type"] for s in trail["timeline"]]
    for expected in ["ReserveStockRequested", "StockReserved", "AuthorizePaymentRequested", "PaymentAuthorized",
                     "OrderConfirmed", "InvoiceIssued", "CapturePaymentRequested", "PaymentCaptured",
                     "ShipmentStatusChanged", "OrderStatusChanged"]:
        assert expected in types, f"{expected} faltando na trilha"
    assert types.index("PaymentAuthorized") < types.index("OrderConfirmed") < types.index("InvoiceIssued")
    seller_side = [s["type"] for s in trail["sellerSideBySku"][sku]]
    assert "SellerItemPublished" in seller_side and "CatalogItemUpserted" in seller_side
    # causa e efeito: todo passo (menos o primeiro) aponta para a mensagem que o causou
    assert all(s["causationId"] for s in trail["timeline"][1:] if s["type"] not in ("ShipmentStatusChanged",))


def test_multi_seller_order_gets_one_invoice_and_one_shipment_per_seller(run):
    a = mp.Seller(seller_id=f"loja-a-{run}")
    b = mp.Seller(seller_id=f"loja-b-{run}")
    mp.publish_and_wait(a, [{"sku": f"A-{run}", "title": "Livro", "price": 40, "stockQty": 5}])
    mp.publish_and_wait(b, [{"sku": f"B-{run}", "title": "Fone", "price": 150, "stockQty": 5}])

    o = order_ready(f"buyer-{run}", [(f"A-{run}", 1), (f"B-{run}", 1)]).json()
    mp.wait_status(o["id"], "INVOICED")
    invs = mp.invoices(o["id"])
    assert sorted(i["sellerId"] for i in invs) == sorted([a.seller_id, b.seller_id])
    assert {mp.money(i["amount"]) for i in invs} == {mp.money(40), mp.money(150)}

    mp.deliver(o["id"], a.seller_id)
    mid = mp.wait_status(o["id"], "IN_DELIVERY")
    assert {p["sellerId"]: p["status"] for p in mid["sellers"]}[a.seller_id] == "DELIVERED"
    mp.deliver(o["id"], b.seller_id)
    mp.wait_status(o["id"], "DELIVERED")
    mp.wait_email(a.email, "Voce vendeu")
    mp.wait_email(b.email, "Voce vendeu")
