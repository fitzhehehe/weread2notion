package com.huawei.hms.iap;

import android.app.Activity;

public class Iap {
    public static IapClient getIapClient(Activity activity) { return new IapClient(); }
}
