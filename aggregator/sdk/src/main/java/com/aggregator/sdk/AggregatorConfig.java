package com.aggregator.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 打进渠道包的 aggregator.json。不含渠道密钥。 */
public record AggregatorConfig(
        String appId,
        String channelCode,
        String serverBaseUrl,
        boolean serverReward,
        String adapterClass,
        Map<String, String> placements) {

    public AggregatorConfig {
        placements = placements == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(placements));
        serverBaseUrl = serverBaseUrl == null ? "" : serverBaseUrl.trim();
        serverReward = serverReward;
        adapterClass = adapterClass == null ? "" : adapterClass;
    }

    public boolean hasServer() {
        return !serverBaseUrl.isBlank();
    }

    public static AggregatorConfig fromJson(String json) {
        Map<String, Object> object = Json.object(json);
        return new AggregatorConfig(
                Json.text(object, "appId"),
                Json.text(object, "channelCode"),
                Json.text(object, "serverBaseUrl"),
                Json.flag(object, "serverReward"),
                Json.text(object, "adapterClass"),
                Json.stringMap(object.get("placements")));
    }
}
