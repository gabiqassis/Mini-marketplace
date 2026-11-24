package br.com.marketplace.notification.domain;

import br.com.marketplace.notification.domain.Draft.Recipient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Regra pura: qual fato gera qual aviso, para quem, com qual texto. Sem framework, testavel.
 * (Os parametros sao primitivos de proposito: o dominio nao conhece os contratos de evento.)
 */
public final class NotificationPlanner {

    public record SoldItem(String title, int quantity) {}

    public record SellerSale(String sellerId, BigDecimal amount, List<SoldItem> items) {}

    public List<Draft> orderConfirmed(UUID orderId, String buyerId, BigDecimal total, List<SellerSale> sales) {
        List<Draft> drafts = new ArrayList<>();
        for (SellerSale sale : sales) {
            StringBuilder items = new StringBuilder();
            sale.items().forEach(i -> items.append("- ").append(i.quantity()).append("x ").append(i.title()).append('\n'));
            drafts.add(new Draft(Recipient.SELLER, sale.sellerId(), "SALE",
                    "Voce vendeu! Pedido " + shortId(orderId),
                    "Parabens! Voce vendeu no pedido " + orderId + " (R$ " + sale.amount() + "):\n" + items
                            + "A nota fiscal esta sendo emitida. Prepare o envio."));
        }
        drafts.add(new Draft(Recipient.BUYER, buyerId, "ORDER_CONFIRMED", "Pedido " + shortId(orderId) + " confirmado",
                "Seu pagamento de R$ " + total + " foi aprovado e o pedido " + orderId + " esta confirmado."));
        return drafts;
    }

    public List<Draft> orderStatusChanged(UUID orderId, String buyerId, String status, String reason) {
        return switch (status) {
            case "CANCELLED" -> List.of(new Draft(Recipient.BUYER, buyerId, "ORDER_CANCELLED",
                    "Pedido " + shortId(orderId) + " cancelado",
                    "Nao conseguimos concluir o pedido " + orderId + ". Motivo: " + humanReason(reason)
                            + ". Nenhum valor foi cobrado."));
            case "REFUNDED" -> List.of(new Draft(Recipient.BUYER, buyerId, "ORDER_REFUNDED",
                    "Reembolso do pedido " + shortId(orderId),
                    "O reembolso do pedido " + orderId + " foi concluido."));
            default -> List.of();
        };
    }

    public List<Draft> invoiceIssued(UUID orderId, String sellerId, long number, String accessKey) {
        return List.of(new Draft(Recipient.SELLER, sellerId, "INVOICE_ISSUED", "NF-e " + number + " autorizada",
                "A NF-e " + number + " do pedido " + orderId + " foi autorizada. Chave de acesso: " + accessKey
                        + ". A etiqueta de envio ja esta disponivel."));
    }

    public List<Draft> invoiceRejected(UUID orderId, String sellerId, String code, String reason) {
        return List.of(new Draft(Recipient.SELLER, sellerId, "INVOICE_REJECTED", "ACAO NECESSARIA: NF-e rejeitada",
                "A NF-e do pedido " + orderId + " foi rejeitada (" + code + "): " + reason
                        + ". Regularize o cadastro fiscal para liberarmos o envio."));
    }

    public List<Draft> shipmentStatusChanged(UUID orderId, String buyerId, String sellerId, String status, String tracking, String location) {
        String id = shortId(orderId);
        return switch (status) {
            case "CREATED" -> List.of(buyer(buyerId, "SHIPMENT_CREATED", "Pedido " + id + " preparado",
                    "Seu pedido foi preparado para envio. Codigo de rastreio: " + tracking + "."));
            case "IN_TRANSIT" -> List.of(buyer(buyerId, "SHIPMENT_IN_TRANSIT", "Pedido " + id + " a caminho",
                    "Sua encomenda " + tracking + " esta a caminho. Ultima localizacao: " + location + "."));
            case "OUT_FOR_DELIVERY" -> List.of(buyer(buyerId, "SHIPMENT_OUT_FOR_DELIVERY", "Pedido " + id + " saiu para entrega",
                    "Sua encomenda " + tracking + " saiu para entrega hoje."));
            case "DELIVERY_FAILED" -> List.of(buyer(buyerId, "SHIPMENT_DELIVERY_FAILED", "Tentativa de entrega do pedido " + id,
                    "Nao conseguimos entregar a encomenda " + tracking + " (" + location + "). Faremos nova tentativa."));
            case "DELIVERED" -> List.of(
                    buyer(buyerId, "SHIPMENT_DELIVERED", "Pedido " + id + " entregue", "Sua encomenda " + tracking + " foi entregue."),
                    new Draft(Recipient.SELLER, sellerId, "SELLER_DELIVERED", "Pedido " + id + " entregue",
                            "A encomenda " + tracking + " foi entregue ao comprador."));
            case "LOST" -> List.of(buyer(buyerId, "SHIPMENT_LOST", "Problema com o pedido " + id,
                    "A encomenda " + tracking + " foi extraviada pela transportadora. O reembolso ja foi solicitado."));
            case "RETURNED" -> List.of(
                    buyer(buyerId, "SHIPMENT_RETURNED", "Pedido " + id + " devolvido",
                            "A encomenda " + tracking + " voltou ao vendedor. O reembolso ja foi solicitado."),
                    new Draft(Recipient.SELLER, sellerId, "SELLER_RETURNED", "Devolucao do pedido " + id,
                            "A encomenda " + tracking + " esta voltando para voce. O estoque sera reposto."));
            default -> List.of();
        };
    }

    private static Draft buyer(String buyerId, String template, String subject, String body) {
        return new Draft(Recipient.BUYER, buyerId, template, subject, body);
    }

    static String humanReason(String reason) {
        if (reason == null) {
            return "nao informado";
        }
        if (reason.startsWith("INSUFFICIENT_STOCK")) {
            return "o produto esgotou enquanto voce finalizava a compra";
        }
        if (reason.startsWith("PAYMENT_")) {
            return "o pagamento nao foi aprovado";
        }
        if (reason.startsWith("TIMEOUT")) {
            return "o pedido expirou antes da confirmacao";
        }
        return reason;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
