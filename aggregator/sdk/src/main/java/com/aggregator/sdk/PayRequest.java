package com.aggregator.sdk;

public record PayRequest(
        String orderId,
        String productId,
        String gameOrderId,
        String channelUserId,
        String roleId,
        long amountFen) {

    public PayRequest withOrder(String orderId, long amountFen) {
        return new PayRequest(orderId, productId, gameOrderId, channelUserId, roleId, amountFen);
    }
}
