package com.aggregator.sdk;

/**
 * 一个渠道一份实现。登录、角色和支付界面都在这里调用渠道 SDK。
 * 适配器之间不互相引用，也不要把渠道用户号上报给发行服务。
 */
public interface ChannelAdapter {
    String channelCode();

    void login(LoginCallback callback);

    void submitRole(RoleInfo role, ResultCallback callback);

    void pay(PayRequest request, ChannelPayCallback callback);

    void loadAd(String adType, String channelSlotId, AdCallback callback);

    void showAd(String adType, String channelSlotId, AdCallback callback);

    default void logout(ResultCallback callback) {
        callback.onSuccess();
    }

    default void exit(ResultCallback callback) {
        callback.onSuccess();
    }
}
