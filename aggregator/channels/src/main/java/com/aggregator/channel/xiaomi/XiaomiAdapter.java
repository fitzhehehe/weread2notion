package com.aggregator.channel.xiaomi;

import android.app.Activity;
import com.aggregator.channel.Ui;
import com.aggregator.sdk.AdCallback;
import com.aggregator.sdk.AdEvent;
import com.aggregator.sdk.ChannelAdapter;
import com.aggregator.sdk.ChannelPayCallback;
import com.aggregator.sdk.LoginCallback;
import com.aggregator.sdk.LoginResult;
import com.aggregator.sdk.PayRequest;
import com.aggregator.sdk.ResultCallback;
import com.aggregator.sdk.RoleInfo;
import com.xiaomi.gamecenter.sdk.MiCommplatform;
import com.xiaomi.gamecenter.sdk.MiErrorCode;
import com.xiaomi.gamecenter.sdk.OnLoginProcessListener;
import com.xiaomi.gamecenter.sdk.OnPayProcessListener;
import com.xiaomi.gamecenter.sdk.entry.MiAccountInfo;
import com.xiaomi.gamecenter.sdk.entry.MiBuyInfo;

/**
 * 小米游戏联运 SDK（miLogin / miUniPay）。
 * 联运 2.0 的 Game Service 和 IAP 是另一套包，不在这个类里。
 * 现行游戏联运文档没有单独的角色上报接口。
 */
public final class XiaomiAdapter implements ChannelAdapter {
    @Override
    public String channelCode() {
        return "xiaomi";
    }

    @Override
    public void login(LoginCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onFailure("缺少 Activity");
            return;
        }
        Ui.onMain(activity, () -> MiCommplatform.getInstance().miLogin(activity, new OnLoginProcessListener() {
            @Override
            public void finishLoginProcess(int code, MiAccountInfo account) {
                if (code == MiErrorCode.MI_XIAOMI_PAYMENT_SUCCESS && account != null) {
                    callback.onSuccess(new LoginResult(
                            "xiaomi",
                            String.valueOf(account.getUid()),
                            account.getSessionId() == null ? "" : account.getSessionId(),
                            account.getNikename() == null ? "" : account.getNikename()));
                    return;
                }
                callback.onFailure("小米登录失败 " + code);
            }
        }));
    }

    @Override
    public void submitRole(RoleInfo role, ResultCallback callback) {
        callback.onSuccess();
    }

    @Override
    public void pay(PayRequest request, ChannelPayCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onResult(false, "缺少 Activity");
            return;
        }
        if (request.amountFen() < 100 || request.amountFen() % 100 != 0) {
            callback.onResult(false, "小米按元计费，金额需要是整元");
            return;
        }
        String orderId = request.orderId() == null || request.orderId().isBlank() ? request.gameOrderId() : request.orderId();
        MiBuyInfo buyInfo = new MiBuyInfo();
        buyInfo.setCpOrderId(orderId);
        buyInfo.setCpUserInfo(request.roleId() == null ? "" : request.roleId());
        buyInfo.setAmount((int) (request.amountFen() / 100));
        Ui.onMain(activity, () -> MiCommplatform.getInstance().miUniPay(activity, buyInfo, new OnPayProcessListener() {
            @Override
            public void finishPayProcess(int code) {
                callback.onResult(code == MiErrorCode.MI_XIAOMI_PAYMENT_SUCCESS, "小米支付结果 " + code);
            }
        }));
    }

    @Override
    public void loadAd(String adType, String channelSlotId, AdCallback callback) {
        callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "小米游戏联运适配器不包含广告 SDK"));
    }

    @Override
    public void showAd(String adType, String channelSlotId, AdCallback callback) {
        callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "小米游戏联运适配器不包含广告 SDK"));
    }
}
