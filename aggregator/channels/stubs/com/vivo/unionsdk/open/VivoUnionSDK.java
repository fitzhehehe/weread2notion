package com.vivo.unionsdk.open;

import android.app.Activity;
import android.content.Context;
import java.util.List;

/** 只给适配器编译用。打包时换成 vivo 官方 aar。 */
public class VivoUnionSDK {
    public static void onPrivacyAgreed(Context context) {}

    public static void registerAccountCallback(Activity activity, VivoAccountCallback callback) {}

    public static void login(Activity activity) {}

    public static void reportRoleInfo(VivoRoleInfo roleInfo) {}

    public static void payV2(Activity activity, VivoPayInfo payInfo, VivoPayCallback callback) {}

    public static void queryMissOrderResult(String openId) {}

    public static void exit(Activity activity, VivoExitCallback callback) {}

    public static void reportOrderComplete(List<String> transNos) {}
}
