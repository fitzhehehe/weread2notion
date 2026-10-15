package com.aggregator.sdk;

public interface ResultCallback {
    void onSuccess();

    void onFailure(String message);
}
