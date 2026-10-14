package com.aggregator.sdk;

public interface LoginCallback {
    void onSuccess(LoginResult result);

    void onFailure(String message);
}
