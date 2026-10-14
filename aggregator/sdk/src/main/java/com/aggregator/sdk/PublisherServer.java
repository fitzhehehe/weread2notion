package com.aggregator.sdk;

import java.util.Map;

/** 发行服务。登录不经过这里。 */
public interface PublisherServer {
    CreatedOrder createOrder(PayRequest request);

    Map<String, String> fetchPlacements(String channelCode);

    void recordReward(String placementId, String channelUserId, String playId);
}
