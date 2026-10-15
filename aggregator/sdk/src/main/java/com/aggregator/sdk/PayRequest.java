package com.aggregator.sdk;

public record PayRequest(
        String orderId,
        String productId,
        String gameOrderId,
        String channelUserId,
        String roleId,
        long amountFen,
        String paySignature,
        String notifyUrl) {

    public PayRequest(
            String orderId,
            String productId,
            String gameOrderId,
            String channelUserId,
            String roleId,
            long amountFen) {
        this(orderId, productId, gameOrderId, channelUserId, roleId, amountFen, "", "");
    }

    public PayRequest withOrder(String orderId, long amountFen) {
        return new PayRequest(
                orderId, productId, gameOrderId, channelUserId, roleId, amountFen, paySignature, notifyUrl);
    }

    public PayRequest withServerPay(String orderId, long amountFen, String paySignature, String notifyUrl) {
        return new PayRequest(
                orderId,
                productId,
                gameOrderId,
                channelUserId,
                roleId,
                amountFen,
                paySignature == null ? "" : paySignature,
                notifyUrl == null ? "" : notifyUrl);
    }
}
