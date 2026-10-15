package com.aggregator.sdk;

public record CreatedOrder(
        String orderId, long amountFen, String productId, String status, String paySignature, String notifyUrl) {

    public CreatedOrder(String orderId, long amountFen, String productId, String status) {
        this(orderId, amountFen, productId, status, "", "");
    }
}
