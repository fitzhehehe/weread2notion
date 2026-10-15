package com.aggregator.sdk;

/** 当前游戏 Activity。渠道适配器在登录和支付时取用。 */
public final class Host {
    private static volatile Object activity;

    private Host() {}

    public static void setActivity(Object activity) {
        Host.activity = activity;
    }

    public static Object activity() {
        return activity;
    }
}
