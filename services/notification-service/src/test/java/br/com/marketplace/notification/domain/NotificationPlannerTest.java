package br.com.marketplace.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPlannerTest {

    private final NotificationPlanner planner = new NotificationPlanner();
    private final UUID orderId = UUID.randomUUID();

    @Test
    void saleNotifiesEverySellerAndTheBuyer() {
        List<Draft> drafts = planner.orderConfirmed(orderId, "buyer", new BigDecimal("30"), List.of(
                new NotificationPlanner.SellerSale("loja-1", BigDecimal.TEN, List.of(new NotificationPlanner.SoldItem("Camiseta", 1))),
                new NotificationPlanner.SellerSale("loja-2", new BigDecimal("20"), List.of(new NotificationPlanner.SoldItem("Caneca", 2)))));

        assertThat(drafts).extracting(Draft::template).containsExactly("SALE", "SALE", "ORDER_CONFIRMED");
        assertThat(drafts.get(0).recipientId()).isEqualTo("loja-1");
        assertThat(drafts.get(1).body()).contains("2x Caneca");
    }

    @Test
    void soldOutCancellationExplainsTheReasonToTheBuyer() {
        Draft d = planner.orderStatusChanged(orderId, "buyer", "CANCELLED", "INSUFFICIENT_STOCK:SKU-1").getFirst();
        assertThat(d.recipient()).isEqualTo(Draft.Recipient.BUYER);
        assertThat(d.body()).contains("esgotou").contains("Nenhum valor foi cobrado");
    }

    @Test
    void intermediateStatusesDoNotSpamTheBuyer() {
        assertThat(planner.orderStatusChanged(orderId, "b", "AWAITING_PAYMENT", null)).isEmpty();
        assertThat(planner.shipmentStatusChanged(orderId, "b", "s", "PICKED_UP", "T", "x")).isEmpty();
    }

    @Test
    void returnNotifiesBuyerAndSeller() {
        assertThat(planner.shipmentStatusChanged(orderId, "b", "loja", "RETURNED", "MM1BR", "x"))
                .extracting(Draft::recipient).containsExactly(Draft.Recipient.BUYER, Draft.Recipient.SELLER);
    }

    @Test
    void rejectedInvoiceAsksSellerToAct() {
        Draft d = planner.invoiceRejected(orderId, "loja", "203", "emissor nao habilitado").getFirst();
        assertThat(d.subject()).contains("ACAO NECESSARIA");
        assertThat(d.recipientId()).isEqualTo("loja");
    }
}
