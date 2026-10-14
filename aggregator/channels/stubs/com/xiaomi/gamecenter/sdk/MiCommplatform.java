package com.xiaomi.gamecenter.sdk;

import android.app.Activity;
import com.xiaomi.gamecenter.sdk.entry.MiBuyInfo;

/** 只给适配器编译用。打包时换成小米游戏联运 SDK。 */
public class MiCommplatform {
    public static MiCommplatform getInstance() { return new MiCommplatform(); }

    public void miLogin(Activity activity, OnLoginProcessListener listener) {}

    public int miUniPay(Activity activity, MiBuyInfo buyInfo, OnPayProcessListener listener) { return 0; }
}
