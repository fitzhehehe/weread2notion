package com.aggregator.sdk;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 渠道登录或支付需要 Activity 结果时，由游戏 Activity 转发到这里。 */
public final class ActivityResults {
    public interface Listener {
        boolean onActivityResult(int requestCode, int resultCode, Object data);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ActivityResults() {}

    public static void add(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void dispatch(int requestCode, int resultCode, Object data) {
        for (Listener listener : LISTENERS) {
            if (listener.onActivityResult(requestCode, resultCode, data)) {
                return;
            }
        }
    }
}
