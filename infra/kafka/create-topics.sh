#!/bin/bash
# Cria os topicos (e as DLTs) antes dos servicos subirem.
# 3 particoes: permite 3 consumidores em paralelo por grupo; ordem garantida por chave (sku/orderId).
# Particionar a mais no inicio e mais barato do que adicionar depois (adicionar forca rebalance).
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"
KT=/opt/kafka/bin/kafka-topics.sh

until $KT --bootstrap-server "$BOOTSTRAP" --list >/dev/null 2>&1; do
  echo "aguardando kafka em $BOOTSTRAP..."
  sleep 2
done

TOPICS="
seller.profile-updated.v1
seller.item-published.v1
catalog.item-upserted.v1
inventory.reserve-requested.v1
inventory.commit-requested.v1
inventory.release-requested.v1
inventory.restock-requested.v1
inventory.stock-reserved.v1
inventory.reservation-rejected.v1
inventory.stock-released.v1
inventory.stock-restocked.v1
payment.authorize-requested.v1
payment.capture-requested.v1
payment.refund-requested.v1
payment.authorized.v1
payment.declined.v1
payment.captured.v1
payment.refunded.v1
order.confirmed.v1
order.status-changed.v1
invoice.issued.v1
invoice.rejected.v1
shipping.status-changed.v1
"

for t in $TOPICS; do
  $KT --bootstrap-server "$BOOTSTRAP" --create --if-not-exists --topic "$t" --partitions 3 --replication-factor 1
  $KT --bootstrap-server "$BOOTSTRAP" --create --if-not-exists --topic "$t.DLT" --partitions 3 --replication-factor 1
done

echo "topicos criados"
