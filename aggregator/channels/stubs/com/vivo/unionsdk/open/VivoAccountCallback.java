package com.vivo.unionsdk.open;

public interface VivoAccountCallback {
    void onVivoAccountLogin(String username, String openid, String authToken);

    void onVivoAccountLogout(int code);

    void onVivoAccountLoginCancel();
}
