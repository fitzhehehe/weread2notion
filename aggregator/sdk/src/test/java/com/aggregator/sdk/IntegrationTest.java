package com.aggregator.sdk;

import com.aggregator.test.TestChannelAdapter;

/** 连到已启动的发行服务，确认单机登录不建单，内购金额以后台商品为准。 */
public final class IntegrationTest {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: IntegrationTest baseUrl appId");
            System.exit(2);
        }
        String base = args[0];
        String appId = args[1];
        EngineEvents.reset();
        EngineEvents.start("""
                {"appId":"%s","channelCode":"test","serverBaseUrl":"%s","serverReward":false,
                "adapterClass":"com.aggregator.test.TestChannelAdapter","placements":{}}
                """.formatted(appId, base));
        must("init_ok", event());
        EngineEvents.login();
        var login = event();
        must("login_ok", login);
        if (!TestChannelAdapter.CHANNEL_USER_ID.equals(String.valueOf(login.get("channelUserId")))) {
            die("登录应返回渠道用户号");
        }
        EngineEvents.pay("", "gem_6", "local-1", TestChannelAdapter.CHANNEL_USER_ID, "r1");
        var pay = event();
        must("pay_ok", pay);
        if (!"awaiting_notify".equals(String.valueOf(pay.get("kind")))) {
            die("已配置服务器时应等待通知，实际 " + pay.get("kind"));
        }
        if (Boolean.TRUE.equals(pay.get("deliverable"))) {
            die("客户端被标记成可发货");
        }
        if (!Long.valueOf(600).equals(number(pay.get("amountFen")))) {
            die("金额应是后台的 600 分，实际 " + pay.get("amountFen"));
        }
        EngineEvents.loadAd("reward", "reward_main");
        var loaded = event();
        if (!"onLoaded".equals(String.valueOf(loaded.get("adName")))) {
            die("广告位映射未生效 " + loaded);
        }
        System.out.println("integration ok " + pay.get("orderId"));
    }

    private static java.util.Map<String, Object> event() {
        String raw = EngineEvents.poll();
        if (raw == null) {
            die("没有事件");
        }
        return Json.object(raw);
    }

    private static void must(String name, java.util.Map<String, Object> event) {
        if (!name.equals(String.valueOf(event.get("name")))) {
            die("期望 " + name + " 实际 " + event);
        }
    }

    private static Long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static void die(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
