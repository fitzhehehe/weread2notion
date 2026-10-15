package com.aggregator.channel;

import android.app.Activity;
import com.aggregator.sdk.Host;

public final class Ui {
    private Ui() {}

    public static Activity activity() {
        Object value = Host.activity();
        if (value instanceof Activity activity) {
            return activity;
        }
        return null;
    }

    public static void onMain(Activity activity, Runnable action) {
        activity.runOnUiThread(action);
    }
}
