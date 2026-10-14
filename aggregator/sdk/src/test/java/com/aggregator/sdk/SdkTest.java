package com.aggregator.sdk;

import com.aggregator.test.TestChannelAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class SdkTest {
    private static int failed;

    public static void main(String[] args) {
        loginDoesNotCallServer();
        roleDoesNotCallServer();
        payWithoutServerIsNotDeliverable();
        payUsesServerAmountAndStillCannotDeliverOnClient();
        unknownPlacementFails();
        rewardUsesPackedSlotZero();
        serverRewardIsRecordedOnlyWhenEnabled();
        offlineSampleButtons();
        signVector();
        if (failed > 0) {
            System.err.println(failed + " failed");
            System.exit(1);
        }
        System.out.println("sdk ok");
    }

    private static void loginDoesNotCallServer() {
        AtomicInteger calls = new AtomicInteger();
        AggregatorSdk sdk = sdk(server(calls), true);
        sdk.init(ok());
        eq(1, calls.get(), "init 拉取广告位");
        sdk.login(new LoginCallback() {
            @Override
            public void onSuccess(LoginResult result) {
                eq("test", result.channelCode(), "渠道");
                eq(TestChannelAdapter.CHANNEL_USER_ID, result.channelUserId(), "渠道用户号");
            }

            @Override
            public void onFailure(String message) {
                fail("登录失败 " + message);
            }
        });
        eq(1, calls.get(), "登录不再访问发行服务");
    }

    private static void roleDoesNotCallServer() {
        TestChannelAdapter adapter = new TestChannelAdapter();
        AggregatorSdk sdk = new AggregatorSdk(offlineConfig(), adapter, null);
        sdk.init(ok());
        sdk.submitRole(new RoleInfo("create", "r1", "角色", 1, "1", "一区"), ok());
        eq("create", adapter.lastRole().type(), "角色类型");
    }

    private static void payWithoutServerIsNotDeliverable() {
        AggregatorSdk sdk = AggregatorSdk.create(offlineConfig(), new TestChannelAdapter());
        sdk.init(ok());
        sdk.pay(new PayRequest("", "gem_6", "g1", "u", "r", 1), result -> {
            eq("client_only", result.kind(), "无服务器支付");
            eq(false, result.deliverable(), "客户端不可发货");
            eq(true, result.channelUiOk(), "模拟收银台");
        });
    }

    private static void payUsesServerAmountAndStillCannotDeliverOnClient() {
        PublisherServer server = new PublisherServer() {
            @Override
            public CreatedOrder createOrder(PayRequest request) {
                eq("gem_6", request.productId(), "商品");
                return new CreatedOrder("o_1", 600, "gem_6", "created");
            }

            @Override
            public Map<String, String> fetchPlacements(String channelCode) {
                return Map.of("reward_main", "0");
            }

            @Override
            public void recordReward(String placementId, String channelUserId, String playId) {
                fail("这次支付不应记激励");
            }
        };
        AggregatorConfig config = new AggregatorConfig("g1", "test", "http://127.0.0.1", false, "", Map.of(), Map.of());
        AggregatorSdk sdk = new AggregatorSdk(config, new TestChannelAdapter(), server);
        sdk.init(ok());
        sdk.pay(new PayRequest("", "gem_6", "g1", "u", "r", 1), result -> {
            eq("awaiting_notify", result.kind(), "等待渠道通知");
            eq(false, result.deliverable(), "客户端不可发货");
            eq("o_1", result.orderId(), "订单号");
            eq(600L, result.amountFen(), "金额来自服务器");
        });
    }

    private static void unknownPlacementFails() {
        AggregatorSdk sdk = AggregatorSdk.create(offlineConfig(), new TestChannelAdapter());
        sdk.init(ok());
        sdk.loadAd("reward", "missing", event -> eq("未知广告位", event.detail(), "广告位"));
    }

    private static void rewardUsesPackedSlotZero() {
        List<String> names = new ArrayList<>();
        AggregatorSdk sdk = AggregatorSdk.create(offlineConfig(), new TestChannelAdapter());
        sdk.init(ok());
        sdk.loadAd("reward", "reward_main", event -> {
            names.add(event.name());
            eq("0", event.channelSlotId(), "代码位");
        });
        sdk.showAd("reward", "reward_main", event -> names.add(event.name()));
        eq("onLoaded", names.get(0), "加载");
        eq("onReward", names.get(1), "发奖回调");
    }

    private static void serverRewardIsRecordedOnlyWhenEnabled() {
        AtomicInteger rewards = new AtomicInteger();
        PublisherServer server = new PublisherServer() {
            @Override
            public CreatedOrder createOrder(PayRequest request) {
                throw new IllegalStateException("未建单");
            }

            @Override
            public Map<String, String> fetchPlacements(String channelCode) {
                return Map.of("reward_main", "0");
            }

            @Override
            public void recordReward(String placementId, String channelUserId, String playId) {
                rewards.incrementAndGet();
                eq("reward_main", placementId, "广告位");
                eq(TestChannelAdapter.CHANNEL_USER_ID, channelUserId, "渠道用户");
            }
        };
        AggregatorConfig config = new AggregatorConfig("g1", "test", "http://127.0.0.1", true, "", Map.of(), Map.of());
        AggregatorSdk sdk = new AggregatorSdk(config, new TestChannelAdapter(), server);
        sdk.init(ok());
        sdk.login(new LoginCallback() {
            @Override
            public void onSuccess(LoginResult result) {}

            @Override
            public void onFailure(String message) {
                fail(message);
            }
        });
        sdk.loadAd("reward", "reward_main", event -> {});
        sdk.showAd("reward", "reward_main", event -> {});
        eq(1, rewards.get(), "网游激励记一笔");
    }

    private static void offlineSampleButtons() {
        EngineEvents.reset();
        EngineEvents.start("""
                {"appId":"g1","channelCode":"test","serverBaseUrl":"","serverReward":false,
                "adapterClass":"com.aggregator.test.TestChannelAdapter","placements":{"reward_main":"0"}}
                """);
        EngineEvents.login();
        EngineEvents.submitRole("create", "r1", "角色", 1, "1", "一区");
        EngineEvents.submitRole("enter", "r1", "角色", 1, "1", "一区");
        EngineEvents.submitRole("levelUp", "r1", "角色", 2, "1", "一区");
        EngineEvents.pay("", "gem_6", "local-1", TestChannelAdapter.CHANNEL_USER_ID, "r1");
        EngineEvents.loadAd("reward", "reward_main");
        EngineEvents.showAd("reward", "reward_main");
        eq("init_ok", name(), "初始化");
        eq("login_ok", name(), "登录");
        eq("role_ok", name(), "创角");
        eq("role_ok", name(), "进服");
        eq("role_ok", name(), "升级");
        Map<String, Object> pay = Json.object(EngineEvents.poll());
        eq("pay_ok", String.valueOf(pay.get("name")), "支付");
        eq("client_only", String.valueOf(pay.get("kind")), "单机支付");
        eq(Boolean.FALSE, pay.get("deliverable"), "不发货");
        eq("onLoaded", adName(), "广告加载");
        eq("onReward", adName(), "广告奖励");
        eq("onClosed", adName(), "广告关闭");
    }

    private static void signVector() {
        String order = Sign.hmac("app-key", "g1", "order-1", "test", "cu1", "gem_6", "role-1", "1710000000");
        eq("1ad1d4ddc7aba117bad4f343ff7d7aa49b5bde0a2d45f6d2594f344161ee52fb", order, "下单签名");
        String notify = Sign.hmac("channel-sec", "g1", "o1", "600", "1710000000");
        eq("a1ae10f5fa3a910be2a3ecb2c6132cbe5ddcb3a253873fe714523a7e93af1a58", notify, "支付通知签名");
    }

    private static String name() {
        return Json.text(Json.object(EngineEvents.poll()), "name");
    }

    private static String adName() {
        return Json.text(Json.object(EngineEvents.poll()), "adName");
    }

    private static AggregatorSdk sdk(PublisherServer server, boolean withServerFlag) {
        AggregatorConfig config = new AggregatorConfig(
                "g1", "test", withServerFlag ? "http://127.0.0.1" : "", false, "", Map.of("reward_main", "0"), Map.of());
        return new AggregatorSdk(config, new TestChannelAdapter(), withServerFlag ? server : null);
    }

    private static PublisherServer server(AtomicInteger calls) {
        return new PublisherServer() {
            @Override
            public CreatedOrder createOrder(PayRequest request) {
                calls.incrementAndGet();
                throw new IllegalStateException("登录路径不应建单");
            }

            @Override
            public Map<String, String> fetchPlacements(String channelCode) {
                calls.incrementAndGet();
                return Map.of("reward_main", "0");
            }

            @Override
            public void recordReward(String placementId, String channelUserId, String playId) {
                calls.incrementAndGet();
            }
        };
    }

    private static AggregatorConfig offlineConfig() {
        return new AggregatorConfig(
                "g1",
                "test",
                "",
                false,
                "com.aggregator.test.TestChannelAdapter",
                Map.of("reward_main", "0"),
                Map.of());
    }

    private static ResultCallback ok() {
        return new ResultCallback() {
            @Override
            public void onSuccess() {}

            @Override
            public void onFailure(String message) {
                fail(message);
            }
        };
    }

    private static void eq(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            fail(label + " expected " + expected + " but " + actual);
        }
    }

    private static void fail(String message) {
        failed++;
        System.err.println(message);
    }
}
