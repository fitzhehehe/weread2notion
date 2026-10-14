package com.aggregator.test;

import com.aggregator.sdk.AdCallback;
import com.aggregator.sdk.AdEvent;
import com.aggregator.sdk.ChannelAdapter;
import com.aggregator.sdk.ChannelPayCallback;
import com.aggregator.sdk.LoginCallback;
import com.aggregator.sdk.LoginResult;
import com.aggregator.sdk.ResultCallback;
import com.aggregator.sdk.RoleInfo;
import com.aggregator.sdk.PayRequest;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 母包测试渠道。登录在本地完成，不访问发行服务，也不访问任何渠道服务器。 */
public final class TestChannelAdapter implements ChannelAdapter {
    public static final String CHANNEL_USER_ID = "test_user_001";
    private final Set<String> loaded = ConcurrentHashMap.newKeySet();
    private RoleInfo lastRole;

    @Override
    public String channelCode() {
        return "test";
    }

    @Override
    public void login(LoginCallback callback) {
        callback.onSuccess(new LoginResult("test", CHANNEL_USER_ID, "test-token", "测试账号"));
    }

    @Override
    public void submitRole(RoleInfo role, ResultCallback callback) {
        if (role.roleId() == null || role.roleId().isBlank()) {
            callback.onFailure("缺少角色 ID");
            return;
        }
        lastRole = role;
        callback.onSuccess();
    }

    @Override
    public void pay(PayRequest request, ChannelPayCallback callback) {
        if (request.productId() == null || request.productId().isBlank()) {
            callback.onResult(false, "缺少商品");
            return;
        }
        callback.onResult(true, "模拟收银台已关闭");
    }

    @Override
    public void loadAd(String adType, String channelSlotId, AdCallback callback) {
        if (!"0".equals(channelSlotId)) {
            callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "测试渠道只接受代码位 0"));
            return;
        }
        loaded.add(adType);
        callback.onEvent(new AdEvent("onLoaded", adType, "", channelSlotId, ""));
    }

    @Override
    public void showAd(String adType, String channelSlotId, AdCallback callback) {
        if (!loaded.contains(adType)) {
            callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "广告未就绪"));
            return;
        }
        if ("reward".equals(adType)) {
            callback.onEvent(new AdEvent("onReward", adType, "", channelSlotId, ""));
        } else {
            callback.onEvent(new AdEvent("onShow", adType, "", channelSlotId, ""));
        }
        callback.onEvent(new AdEvent("onClosed", adType, "", channelSlotId, ""));
    }

    public RoleInfo lastRole() {
        return lastRole;
    }
}
