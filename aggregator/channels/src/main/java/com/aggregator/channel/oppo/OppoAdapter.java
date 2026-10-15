package com.aggregator.channel.oppo;

import android.app.Activity;
import com.aggregator.channel.Ui;
import com.aggregator.sdk.AdCallback;
import com.aggregator.sdk.AdEvent;
import com.aggregator.sdk.ChannelAdapter;
import com.aggregator.sdk.ChannelPayCallback;
import com.aggregator.sdk.ChannelRuntime;
import com.aggregator.sdk.LoginCallback;
import com.aggregator.sdk.LoginResult;
import com.aggregator.sdk.PayRequest;
import com.aggregator.sdk.ResultCallback;
import com.aggregator.sdk.RoleInfo;
import com.aggregator.sdk.Json;
import com.nearme.game.sdk.GameCenterSDK;
import com.nearme.game.sdk.callback.ApiCallback;
import com.nearme.game.sdk.callback.GameExitCallback;
import com.nearme.game.sdk.common.model.biz.PayInfo;
import com.nearme.game.sdk.common.model.biz.ReportUserGameInfoParam;
import java.util.Map;
import java.util.TreeMap;

/**
 * OPPO 游戏中心联运。登录走 GameCenterSDK，不经过发行服务。
 * 广告联盟是另一套 SDK，这个类不调用它。
 */
public final class OppoAdapter implements ChannelAdapter {
    @Override
    public String channelCode() {
        return "oppo";
    }

    @Override
    public void prepare(ResultCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onFailure("缺少 Activity");
            return;
        }
        String secret = ChannelRuntime.param("appSecret");
        if (secret.isBlank()) {
            callback.onFailure("缺少 OPPO appSecret");
            return;
        }
        Ui.onMain(activity, () -> {
            GameCenterSDK.init(secret, activity.getApplicationContext());
            callback.onSuccess();
        });
    }

    @Override
    public void login(LoginCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onFailure("缺少 Activity");
            return;
        }
        Ui.onMain(activity, () -> GameCenterSDK.getInstance().doLogin(activity, new ApiCallback() {
            @Override
            public void onSuccess(String resultMsg) {
                GameCenterSDK.getInstance().doGetTokenAndSsoid(new ApiCallback() {
                    @Override
                    public void onSuccess(String payload) {
                        try {
                            Map<String, Object> json = Json.object(payload == null ? "{}" : payload);
                            String ssoid = Json.text(json, "ssoid");
                            if (ssoid.isBlank()) {
                                callback.onFailure("OPPO 没有返回 ssoid");
                                return;
                            }
                            callback.onSuccess(new LoginResult("oppo", ssoid, Json.text(json, "token"), ""));
                        } catch (RuntimeException e) {
                            callback.onFailure("OPPO 登录结果无法解析");
                        }
                    }

                    @Override
                    public void onFailure(String message, int code) {
                        callback.onFailure(text(message, code));
                    }
                });
            }

            @Override
            public void onFailure(String message, int code) {
                callback.onFailure(text(message, code));
            }
        }));
    }

    @Override
    public void submitRole(RoleInfo role, ResultCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onFailure("缺少 Activity");
            return;
        }
        Ui.onMain(activity, () -> GameCenterSDK.getInstance().doReportUserGameInfoData(
                new ReportUserGameInfoParam(
                        role.roleId(),
                        role.roleName(),
                        role.roleLevel(),
                        role.serverId(),
                        role.serverName(),
                        "",
                        new TreeMap<String, Number>()),
                new ApiCallback() {
                    @Override
                    public void onSuccess(String resultMsg) {
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(String message, int code) {
                        callback.onFailure(text(message, code));
                    }
                }));
    }

    @Override
    public void pay(PayRequest request, ChannelPayCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onResult(false, "缺少 Activity");
            return;
        }
        if (request.amountFen() <= 0 || request.amountFen() > Integer.MAX_VALUE) {
            callback.onResult(false, "OPPO 支付金额无效");
            return;
        }
        String orderId = request.orderId() == null || request.orderId().isBlank() ? request.gameOrderId() : request.orderId();
        PayInfo payInfo = new PayInfo(orderId, orderId, (int) request.amountFen());
        payInfo.setProductName(request.productId());
        payInfo.setProductDesc(request.productId());
        payInfo.setCallbackUrl(ChannelRuntime.param("notifyUrl"));
        Ui.onMain(activity, () -> GameCenterSDK.getInstance().doPay(activity, payInfo, new ApiCallback() {
            @Override
            public void onSuccess(String resultMsg) {
                callback.onResult(true, "OPPO 收银台已关闭");
            }

            @Override
            public void onFailure(String message, int code) {
                callback.onResult(false, text(message, code));
            }
        }));
    }

    @Override
    public void loadAd(String adType, String channelSlotId, AdCallback callback) {
        callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "OPPO 广告联盟不在联运适配器里"));
    }

    @Override
    public void showAd(String adType, String channelSlotId, AdCallback callback) {
        callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "OPPO 广告联盟不在联运适配器里"));
    }

    @Override
    public void exit(ResultCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onFailure("缺少 Activity");
            return;
        }
        Ui.onMain(activity, () -> GameCenterSDK.getInstance().onExit(activity, new GameExitCallback() {
            @Override
            public void exitGame() {
                callback.onSuccess();
            }
        }));
    }

    private static String text(String message, int code) {
        return (message == null ? "" : message) + " (" + code + ")";
    }
}
