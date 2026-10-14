package com.aggregator.sdk;

public record CreatedOrder(String orderId, long amountFen, String productId, String status) {}
