package com.aggregator.sdk;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Unity / Cocos 轮询这条队列，避免在引擎线程里做渠道回调。 */
public final class EngineEvents {
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    private static AggregatorSdk sdk;

    private EngineEvents() {}

    public static synchronized void reset() {
        QUEUE.clear();
        sdk = null;
    }

    public static synchronized void start(String configJson) {
        try {
            AggregatorConfig config = AggregatorConfig.fromJson(configJson);
            ChannelAdapter adapter = (ChannelAdapter) Class.forName(config.adapterClass())
                    .getDeclaredConstructor()
                    .newInstance();
            sdk = AggregatorSdk.create(config, adapter);
            sdk.init(new ResultCallback() {
                @Override
                public void onSuccess() {
                    push(event("init_ok"));
                }

                @Override
                public void onFailure(String message) {
                    push(fail("init_fail", message));
                }
            });
        } catch (Exception e) {
            push(fail("init_fail", e.getMessage()));
        }
    }

    public static void login() {
        AggregatorSdk current = sdk();
        if (current == null) {
            return;
        }
        current.login(new LoginCallback() {
            @Override
            public void onSuccess(LoginResult result) {
                Map<String, Object> event = event("login_ok");
                event.put("channelCode", result.channelCode());
                event.put("channelUserId", result.channelUserId());
                event.put("nickname", result.nickname());
                push(event);
            }

            @Override
            public void onFailure(String message) {
                push(fail("login_fail", message));
            }
        });
    }

    public static void submitRole(
            String type, String roleId, String roleName, int roleLevel, String serverId, String serverName) {
        AggregatorSdk current = sdk();
        if (current == null) {
            return;
        }
        current.submitRole(new RoleInfo(type, roleId, roleName, roleLevel, serverId, serverName), new ResultCallback() {
            @Override
            public void onSuccess() {
                Map<String, Object> event = event("role_ok");
                event.put("type", type);
                push(event);
            }

            @Override
            public void onFailure(String message) {
                push(fail("role_fail", message));
            }
        });
    }

    public static void pay(String orderId, String productId, String gameOrderId, String channelUserId, String roleId) {
        AggregatorSdk current = sdk();
        if (current == null) {
            return;
        }
        current.pay(new PayRequest(orderId, productId, gameOrderId, channelUserId, roleId, 0), result -> {
            Map<String, Object> event = event("failed".equals(result.kind()) ? "pay_fail" : "pay_ok");
            event.put("kind", result.kind());
            event.put("deliverable", result.deliverable());
            event.put("orderId", result.orderId());
            event.put("amountFen", result.amountFen());
            event.put("channelUiOk", result.channelUiOk());
            event.put("message", result.message());
            push(event);
        });
    }

    public static void loadAd(String adType, String placementId) {
        AggregatorSdk current = sdk();
        if (current == null) {
            return;
        }
        current.loadAd(adType, placementId, event -> push(ad(event)));
    }

    public static void showAd(String adType, String placementId) {
        AggregatorSdk current = sdk();
        if (current == null) {
            return;
        }
        current.showAd(adType, placementId, event -> push(ad(event)));
    }

    public static synchronized String poll() {
        return QUEUE.poll();
    }

    private static AggregatorSdk sdk() {
        if (sdk == null) {
            push(fail("init_fail", "先初始化"));
            return null;
        }
        return sdk;
    }

    private static Map<String, Object> ad(AdEvent event) {
        Map<String, Object> out = event("ad");
        out.put("adName", event.name());
        out.put("adType", event.adType());
        out.put("placementId", event.placementId());
        out.put("channelSlotId", event.channelSlotId());
        out.put("detail", event.detail());
        return out;
    }

    private static Map<String, Object> event(String name) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        return out;
    }

    private static Map<String, Object> fail(String name, String message) {
        Map<String, Object> out = event(name);
        out.put("message", message == null ? "" : message);
        return out;
    }

    private static synchronized void push(Map<String, Object> event) {
        QUEUE.add(Json.stringify(event));
    }
}
