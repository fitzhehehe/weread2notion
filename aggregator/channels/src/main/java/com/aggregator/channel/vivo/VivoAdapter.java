package com.aggregator.channel.vivo;

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
import com.vivo.mobilead.unified.base.AdParams;
import com.vivo.mobilead.unified.base.VivoAdError;
import com.vivo.mobilead.unified.base.callback.MediaListener;
import com.vivo.mobilead.unified.reward.UnifiedVivoRewardVideoAd;
import com.vivo.mobilead.unified.reward.UnifiedVivoRewardVideoAdListener;
import com.vivo.unionsdk.open.OrderResultInfo;
import com.vivo.unionsdk.open.VivoAccountCallback;
import com.vivo.unionsdk.open.VivoConstants;
import com.vivo.unionsdk.open.VivoExitCallback;
import com.vivo.unionsdk.open.VivoPayCallback;
import com.vivo.unionsdk.open.VivoPayInfo;
import com.vivo.unionsdk.open.VivoRoleInfo;
import com.vivo.unionsdk.open.VivoUnionSDK;

/** vivo 联运。隐私同意后才能登录。支付签名由服务端放入 paySignature。 */
public final class VivoAdapter implements ChannelAdapter {
    private String openId = "";
    private UnifiedVivoRewardVideoAd rewardAd;
    private boolean rewardReady;

    @Override
    public String channelCode() {
        return "vivo";
    }

    @Override
    public void prepare(ResultCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onFailure("缺少 Activity");
            return;
        }
        Ui.onMain(activity, () -> {
            VivoUnionSDK.onPrivacyAgreed(activity);
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
        Ui.onMain(activity, () -> {
            VivoUnionSDK.registerAccountCallback(activity, new VivoAccountCallback() {
                @Override
                public void onVivoAccountLogin(String username, String openid, String authToken) {
                    openId = openid == null ? "" : openid;
                    if (openId.isBlank()) {
                        callback.onFailure("vivo 没有返回 openId");
                        return;
                    }
                    callback.onSuccess(new LoginResult("vivo", openId, authToken == null ? "" : authToken, username));
                    VivoUnionSDK.queryMissOrderResult(openId);
                }

                @Override
                public void onVivoAccountLogout(int code) {}

                @Override
                public void onVivoAccountLoginCancel() {
                    callback.onFailure("vivo 登录取消");
                }
            });
            VivoUnionSDK.login(activity);
        });
    }

    @Override
    public void submitRole(RoleInfo role, ResultCallback callback) {
        VivoUnionSDK.reportRoleInfo(new VivoRoleInfo(
                role.roleId(),
                String.valueOf(role.roleLevel()),
                role.roleName(),
                role.serverId(),
                role.serverName()));
        callback.onSuccess();
    }

    @Override
    public void pay(PayRequest request, ChannelPayCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onResult(false, "缺少 Activity");
            return;
        }
        if (openId.isBlank()) {
            callback.onResult(false, "vivo 支付前要先登录");
            return;
        }
        if (request.paySignature() == null || request.paySignature().isBlank()) {
            callback.onResult(false, "vivo 支付缺少服务端签名");
            return;
        }
        String notifyUrl = request.notifyUrl() == null || request.notifyUrl().isBlank()
                ? ChannelRuntime.param("notifyUrl")
                : request.notifyUrl();
        if (notifyUrl.isBlank()) {
            callback.onResult(false, "vivo 支付缺少通知地址");
            return;
        }
        String orderId = request.orderId() == null || request.orderId().isBlank() ? request.gameOrderId() : request.orderId();
        VivoPayInfo.Builder builder = new VivoPayInfo.Builder()
                .setAppId(ChannelRuntime.param("channelAppId"))
                .setCpOrderNo(orderId)
                .setNotifyUrl(notifyUrl)
                .setOrderAmount(String.valueOf(request.amountFen()))
                .setProductName(request.productId())
                .setProductDesc(request.productId())
                .setVivoSignature(request.paySignature())
                .setExtUid(openId);
        if (request.roleId() != null && !request.roleId().isBlank()) {
            builder.setRoleId(request.roleId());
        }
        VivoPayInfo payInfo = builder.build();
        Ui.onMain(activity, () -> VivoUnionSDK.payV2(activity, payInfo, new VivoPayCallback() {
            @Override
            public void onVivoPayResult(int code, OrderResultInfo order) {
                // 客户端结果不发货。道具到账后才能 reportOrderComplete。
                callback.onResult(code == VivoConstants.PAYMENT_RESULT_CODE_SUCCESS, "vivo 支付结果 " + code);
            }
        }));
    }

    @Override
    public void loadAd(String adType, String channelSlotId, AdCallback callback) {
        if (!"reward".equals(adType)) {
            callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "vivo 适配器目前只接激励视频"));
            return;
        }
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "缺少 Activity"));
            return;
        }
        rewardReady = false;
        AdParams params = new AdParams.Builder(channelSlotId).build();
        rewardAd = new UnifiedVivoRewardVideoAd(activity, params, new UnifiedVivoRewardVideoAdListener() {
            @Override
            public void onAdReady() {
                rewardReady = true;
                callback.onEvent(new AdEvent("onLoaded", adType, "", channelSlotId, ""));
            }

            @Override
            public void onAdFailed(VivoAdError error) {
                callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, String.valueOf(error)));
            }

            @Override
            public void onAdClick() {}

            @Override
            public void onAdShow() {
                callback.onEvent(new AdEvent("onShow", adType, "", channelSlotId, ""));
            }

            @Override
            public void onAdClose() {
                rewardReady = false;
                callback.onEvent(new AdEvent("onClosed", adType, "", channelSlotId, ""));
            }

            @Override
            public void onRewardVerify() {
                callback.onEvent(new AdEvent("onReward", adType, "", channelSlotId, ""));
            }
        });
        rewardAd.setMediaListener(new MediaListener() {
            @Override
            public void onVideoStart() {}

            @Override
            public void onVideoPause() {}

            @Override
            public void onVideoPlay() {}

            @Override
            public void onVideoError(VivoAdError error) {}

            @Override
            public void onVideoCompletion() {}

            @Override
            public void onVideoCached() {}
        });
        Ui.onMain(activity, () -> rewardAd.loadAd());
    }

    @Override
    public void showAd(String adType, String channelSlotId, AdCallback callback) {
        if (!"reward".equals(adType)) {
            callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "vivo 适配器目前只接激励视频"));
            return;
        }
        Activity activity = Ui.activity();
        if (activity == null || rewardAd == null || !rewardReady) {
            callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "广告未就绪"));
            return;
        }
        Ui.onMain(activity, () -> rewardAd.showAd(activity));
    }

    @Override
    public void exit(ResultCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onFailure("缺少 Activity");
            return;
        }
        Ui.onMain(activity, () -> VivoUnionSDK.exit(activity, new VivoExitCallback() {
            @Override
            public void onExitConfirm() {
                callback.onSuccess();
            }

            @Override
            public void onExitCancel() {
                callback.onFailure("取消退出");
            }
        }));
    }
}
