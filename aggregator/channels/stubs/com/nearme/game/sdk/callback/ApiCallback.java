package com.nearme.game.sdk.callback;

public interface ApiCallback {
    void onSuccess(String resultMsg);

    void onFailure(String resultMsg, int resultCode);
}
