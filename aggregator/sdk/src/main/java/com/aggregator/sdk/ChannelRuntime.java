package com.aggregator.sdk;

import java.util.Map;

/** 当前渠道包的参数。适配器只读这里，不读渠道后台。 */
public final class ChannelRuntime {
    private static Map<String, String> params = Map.of();

    private ChannelRuntime() {}

    public static void bind(AggregatorConfig config) {
        params = config.channelParams();
    }

    public static String param(String key) {
        String value = params.get(key);
        return value == null ? "" : value;
    }
}
