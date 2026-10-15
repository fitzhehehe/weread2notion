package com.aggregator.sdk;

import java.util.Map;
import java.util.UUID;

/**
 * 游戏只调用这里。登录、角色、退出只进入渠道适配器。
 * 配置了 serverBaseUrl 时，支付会先向发行服务建单，发货仍以渠道通知为准。
 */
public final class AggregatorSdk {
    private final AggregatorConfig config;
    private final ChannelAdapter adapter;
    private final PublisherServer server;
    private Map<String, String> placements;
    private boolean ready;
    private String channelUserId = "";

    public AggregatorSdk(AggregatorConfig config, ChannelAdapter adapter, PublisherServer server) {
        this.config = config;
        this.adapter = adapter;
        this.server = server;
        this.placements = config.placements();
    }

    public static AggregatorSdk create(AggregatorConfig config, ChannelAdapter adapter) {
        PublisherServer server = config.hasServer() ? new HttpPublisherServer(config.serverBaseUrl(), config.appId()) : null;
        return new AggregatorSdk(config, adapter, server);
    }

    public void init(ResultCallback callback) {
        if (config.appId() == null || config.appId().isBlank()) {
            callback.onFailure("缺少 appId");
            return;
        }
        if (!config.channelCode().equals(adapter.channelCode())) {
            callback.onFailure("适配器渠道是 " + adapter.channelCode() + "，包配置是 " + config.channelCode());
            return;
        }
        if (server != null) {
            try {
                placements = server.fetchPlacements(config.channelCode());
            } catch (RuntimeException e) {
                callback.onFailure(e.getMessage());
                return;
            }
        }
        ChannelRuntime.bind(config);
        adapter.prepare(new ResultCallback() {
            @Override
            public void onSuccess() {
                ready = true;
                callback.onSuccess();
            }

            @Override
            public void onFailure(String message) {
                callback.onFailure(message);
            }
        });
    }

    public void login(LoginCallback callback) {
        if (!ready) {
            callback.onFailure("先初始化");
            return;
        }
        adapter.login(new LoginCallback() {
            @Override
            public void onSuccess(LoginResult result) {
                channelUserId = result.channelUserId();
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(String message) {
                callback.onFailure(message);
            }
        });
    }

    public void submitRole(RoleInfo role, ResultCallback callback) {
        if (!ready) {
            callback.onFailure("先初始化");
            return;
        }
        adapter.submitRole(role, callback);
    }

    public void pay(PayRequest request, PayCallback callback) {
        if (!ready) {
            callback.onResult(PayResult.fail("先初始化"));
            return;
        }
        if (server == null) {
            adapter.pay(request, (success, message) -> callback.onResult(PayResult.clientOnly(success, message)));
            return;
        }
        PayRequest orderRequest = request;
        if (request.orderId() == null || request.orderId().isBlank()) {
            try {
                CreatedOrder order = createOn(server, request);
                orderRequest = request.withServerPay(
                        order.orderId(), order.amountFen(), order.paySignature(), order.notifyUrl());
            } catch (RuntimeException e) {
                callback.onResult(PayResult.fail(e.getMessage()));
                return;
            }
        }
        PayRequest frozen = orderRequest;
        adapter.pay(frozen, (success, message) -> callback.onResult(
                PayResult.awaitingNotify(frozen.orderId(), frozen.amountFen(), success, message)));
    }

    private CreatedOrder createOn(PublisherServer target, PayRequest request) {
        if (target instanceof HttpPublisherServer http) {
            return http.createOrder(config.channelCode(), request);
        }
        return target.createOrder(request);
    }

    public void loadAd(String adType, String placementId, AdCallback callback) {
        String slot = slot(placementId, callback, adType);
        if (slot == null) {
            return;
        }
        adapter.loadAd(adType, slot, event -> callback.onEvent(tag(event, adType, placementId, slot)));
    }

    public void showAd(String adType, String placementId, AdCallback callback) {
        String slot = slot(placementId, callback, adType);
        if (slot == null) {
            return;
        }
        adapter.showAd(adType, slot, event -> {
            AdEvent tagged = tag(event, adType, placementId, slot);
            if (config.serverReward() && server != null && "onReward".equals(tagged.name())) {
                try {
                    record(placementId);
                } catch (RuntimeException e) {
                    callback.onEvent(new AdEvent("onFailed", adType, placementId, slot, e.getMessage()));
                    return;
                }
            }
            callback.onEvent(tagged);
        });
    }

    private void record(String placementId) {
        String playId = UUID.randomUUID().toString();
        if (server instanceof HttpPublisherServer http) {
            http.recordReward(config.channelCode(), placementId, channelUserId, playId);
        } else {
            server.recordReward(placementId, channelUserId, playId);
        }
    }

    public void logout(ResultCallback callback) {
        adapter.logout(callback);
    }

    public void exit(ResultCallback callback) {
        adapter.exit(callback);
    }

    public String channelUserId() {
        return channelUserId;
    }

    private String slot(String placementId, AdCallback callback, String adType) {
        if (!ready) {
            callback.onEvent(new AdEvent("onFailed", adType, placementId, "", "先初始化"));
            return null;
        }
        String slot = placements.get(placementId);
        if (slot == null || slot.isBlank()) {
            callback.onEvent(new AdEvent("onFailed", adType, placementId, "", "未知广告位"));
            return null;
        }
        return slot;
    }

    private static AdEvent tag(AdEvent event, String adType, String placementId, String slot) {
        return new AdEvent(event.name(), adType, placementId, slot, event.detail());
    }
}
