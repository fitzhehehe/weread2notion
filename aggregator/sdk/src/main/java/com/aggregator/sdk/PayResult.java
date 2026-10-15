package com.aggregator.sdk;

/** deliverable 始终为 false。客户端回调只表示收银台界面结果，不表示可以发货。 */
public record PayResult(
        String kind,
        boolean deliverable,
        String orderId,
        long amountFen,
        boolean channelUiOk,
        String message) {

    public PayResult {
        if (deliverable) {
            throw new IllegalArgumentException("客户端支付结果不能标记为可发货");
        }
    }

    public static PayResult clientOnly(boolean channelUiOk, String message) {
        return new PayResult("client_only", false, "", 0, channelUiOk, message);
    }

    public static PayResult awaitingNotify(String orderId, long amountFen, boolean channelUiOk, String message) {
        return new PayResult("awaiting_notify", false, orderId, amountFen, channelUiOk, message);
    }

    public static PayResult fail(String message) {
        return new PayResult("failed", false, "", 0, false, message);
    }
}
