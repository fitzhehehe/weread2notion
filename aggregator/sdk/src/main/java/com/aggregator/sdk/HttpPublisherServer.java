package com.aggregator.sdk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpPublisherServer implements PublisherServer {
    private final String baseUrl;
    private final String appId;
    private final HttpClient http;

    public HttpPublisherServer(String baseUrl, String appId) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.appId = appId;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public CreatedOrder createOrder(PayRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", appId);
        body.put("channelCode", "");
        body.put("channelUserId", request.channelUserId());
        body.put("productId", request.productId());
        body.put("gameOrderId", request.gameOrderId());
        body.put("roleId", request.roleId() == null ? "" : request.roleId());
        Map<String, Object> parsed = post("/v1/orders", body);
        return new CreatedOrder(
                Json.text(parsed, "orderId"),
                Json.number(parsed, "amountFen"),
                Json.text(parsed, "productId"),
                Json.text(parsed, "status"));
    }

    public CreatedOrder createOrder(String channelCode, PayRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", appId);
        body.put("channelCode", channelCode);
        body.put("channelUserId", request.channelUserId());
        body.put("productId", request.productId());
        body.put("gameOrderId", request.gameOrderId());
        body.put("roleId", request.roleId() == null ? "" : request.roleId());
        Map<String, Object> parsed = post("/v1/orders", body);
        return new CreatedOrder(
                Json.text(parsed, "orderId"),
                Json.number(parsed, "amountFen"),
                Json.text(parsed, "productId"),
                Json.text(parsed, "status"));
    }

    @Override
    public Map<String, String> fetchPlacements(String channelCode) {
        String url = baseUrl + "/v1/config?appId=" + encode(appId) + "&channelCode=" + encode(channelCode);
        Map<String, Object> parsed = get(url);
        return Json.stringMap(parsed.get("placements"));
    }

    @Override
    public void recordReward(String placementId, String channelUserId, String playId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", appId);
        body.put("channelCode", "");
        body.put("channelUserId", channelUserId);
        body.put("placementId", placementId);
        body.put("playId", playId);
        post("/v1/reward-completions", body);
    }

    public void recordReward(String channelCode, String placementId, String channelUserId, String playId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appId", appId);
        body.put("channelCode", channelCode);
        body.put("channelUserId", channelUserId);
        body.put("placementId", placementId);
        body.put("playId", playId);
        post("/v1/reward-completions", body);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                .build();
        return send(request);
    }

    private Map<String, Object> get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return send(request);
    }

    private Map<String, Object> send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> parsed = Json.object(response.body().isBlank() ? "{}" : response.body());
            if (response.statusCode() / 100 != 2) {
                String error = Json.text(parsed, "error");
                throw new IllegalStateException(error.isBlank() ? "发行服务返回 " + response.statusCode() : error);
            }
            return parsed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("发行服务中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("发行服务不可达", e);
        }
    }
}
