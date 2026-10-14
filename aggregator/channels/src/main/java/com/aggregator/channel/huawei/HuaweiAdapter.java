package com.aggregator.channel.huawei;

import android.app.Activity;
import android.content.Intent;
import com.aggregator.channel.Ui;
import com.aggregator.sdk.ActivityResults;
import com.aggregator.sdk.AdCallback;
import com.aggregator.sdk.AdEvent;
import com.aggregator.sdk.ChannelAdapter;
import com.aggregator.sdk.ChannelPayCallback;
import com.aggregator.sdk.LoginCallback;
import com.aggregator.sdk.LoginResult;
import com.aggregator.sdk.PayRequest;
import com.aggregator.sdk.ResultCallback;
import com.aggregator.sdk.RoleInfo;
import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hms.iap.Iap;
import com.huawei.hms.iap.entity.PurchaseIntentReq;
import com.huawei.hms.iap.entity.PurchaseIntentResult;
import com.huawei.hms.jos.AppParams;
import com.huawei.hms.jos.JosApps;
import com.huawei.hms.jos.games.Games;
import com.huawei.hms.jos.games.player.Player;
import com.huawei.hms.support.account.AccountAuthManager;
import com.huawei.hms.support.account.request.AccountAuthParams;
import com.huawei.hms.support.account.result.AuthAccount;
import com.huawei.hms.support.account.service.AccountAuthService;
import com.huawei.hms.utils.ResourceLoaderUtil;

/** 华为应用市场 Android 联运。鸿蒙 NEXT 不走这个类。 */
public final class HuaweiAdapter implements ChannelAdapter, ActivityResults.Listener {
    private static final int SIGN_IN = 8801;
    private static final int PAY = 8802;
    private LoginCallback loginCallback;
    private ChannelPayCallback payCallback;
    private AccountAuthService authService;

    public HuaweiAdapter() {
        ActivityResults.add(this);
    }

    @Override
    public String channelCode() {
        return "huawei";
    }

    @Override
    public void prepare(ResultCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onFailure("缺少 Activity");
            return;
        }
        Ui.onMain(activity, () -> {
            ResourceLoaderUtil.setmContext(activity);
            JosApps.getJosAppsClient(activity)
                    .init(new AppParams(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM_GAME, () -> activity.finish()))
                    .addOnSuccessListener(unused -> {
                        Games.getBuoyClient(activity).showFloatWindow();
                        callback.onSuccess();
                    })
                    .addOnFailureListener(error -> callback.onFailure(error.getMessage()));
        });
    }

    @Override
    public void login(LoginCallback callback) {
        Activity activity = Ui.activity();
        if (activity == null) {
            callback.onFailure("缺少 Activity");
            return;
        }
        loginCallback = callback;
        Ui.onMain(activity, () -> {
            authService = AccountAuthManager.getService(activity, AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM_GAME);
            authService.silentSignIn().addOnSuccessListener(this::loadPlayer).addOnFailureListener(error -> {
                try {
                    activity.startActivityForResult(authService.getSignInIntent(), SIGN_IN);
                } catch (RuntimeException e) {
                    callback.onFailure(e.getMessage());
                }
            });
        });
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
        payCallback = callback;
        PurchaseIntentReq req = new PurchaseIntentReq();
        req.setProductId(request.productId());
        req.setPriceType(0);
        req.setDeveloperPayload(request.orderId() == null ? "" : request.orderId());
        Ui.onMain(activity, () -> Iap.getIapClient(activity).createPurchaseIntent(req).addOnSuccessListener(result -> {
            if (result.getStatus().hasResolution()) {
                try {
                    result.getStatus().startResolutionForResult(activity, PAY);
                } catch (Exception e) {
                    callback.onResult(false, e.getMessage());
                }
                return;
            }
            callback.onResult(false, "华为收银台没有拉起");
        }).addOnFailureListener(error -> callback.onResult(false, error.getMessage())));
    }

    @Override
    public void loadAd(String adType, String channelSlotId, AdCallback callback) {
        callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "华为联运适配器不包含广告 SDK"));
    }

    @Override
    public void showAd(String adType, String channelSlotId, AdCallback callback) {
        callback.onEvent(new AdEvent("onFailed", adType, "", channelSlotId, "华为联运适配器不包含广告 SDK"));
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Object data) {
        if (!(data instanceof Intent intent)) {
            return false;
        }
        if (requestCode == SIGN_IN) {
            AccountAuthManager.parseAuthResultFromIntent(intent)
                    .addOnSuccessListener(this::loadPlayer)
                    .addOnFailureListener(error -> {
                        if (loginCallback != null) {
                            loginCallback.onFailure(error.getMessage());
                        }
                    });
            return true;
        }
        if (requestCode == PAY && payCallback != null) {
            payCallback.onResult(true, "华为收银台已关闭");
            return true;
        }
        return false;
    }

    private void loadPlayer(AuthAccount account) {
        Activity activity = Ui.activity();
        if (activity == null) {
            if (loginCallback != null) {
                loginCallback.onFailure("缺少 Activity");
            }
            return;
        }
        Games.getPlayersClient(activity).getGamePlayer().addOnSuccessListener(new OnSuccessListener<Player>() {
            @Override
            public void onSuccess(Player player) {
                if (loginCallback != null) {
                    loginCallback.onSuccess(new LoginResult(
                            "huawei", player.getPlayerId(), "", player.getDisplayName() == null ? "" : player.getDisplayName()));
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception error) {
                if (loginCallback != null) {
                    loginCallback.onFailure(error.getMessage());
                }
            }
        });
    }
}
