package com.xiaomi.gamecenter.sdk;

import com.xiaomi.gamecenter.sdk.entry.MiAccountInfo;

public interface OnLoginProcessListener {
    void finishLoginProcess(int code, MiAccountInfo account);
}
