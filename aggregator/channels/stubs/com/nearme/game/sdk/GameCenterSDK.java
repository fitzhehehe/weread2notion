package com.nearme.game.sdk;

import android.app.Activity;
import android.content.Context;
import com.nearme.game.sdk.callback.ApiCallback;
import com.nearme.game.sdk.callback.GameExitCallback;
import com.nearme.game.sdk.common.model.biz.PayInfo;
import com.nearme.game.sdk.common.model.biz.ReportUserGameInfoParam;

/** 只给适配器编译用。打包时换成 OPPO 官方 aar，不要把这个类打进渠道包。 */
public class GameCenterSDK {
    public static void init(String appSecret, Context context) {}

    public static GameCenterSDK getInstance() {
        return new GameCenterSDK();
    }

    public void doLogin(Activity activity, ApiCallback callback) {}

    public void doGetTokenAndSsoid(ApiCallback callback) {}

    public void doPay(Activity activity, PayInfo payInfo, ApiCallback callback) {}

    public void doReportUserGameInfoData(ReportUserGameInfoParam param, ApiCallback callback) {}

    public void onExit(Activity activity, GameExitCallback callback) {}
}
