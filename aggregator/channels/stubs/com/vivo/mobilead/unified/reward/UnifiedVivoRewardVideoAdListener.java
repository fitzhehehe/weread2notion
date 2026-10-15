package com.vivo.mobilead.unified.reward;

import com.vivo.mobilead.unified.base.VivoAdError;

public interface UnifiedVivoRewardVideoAdListener {
    void onAdReady();
    void onAdFailed(VivoAdError error);
    void onAdClick();
    void onAdShow();
    void onAdClose();
    void onRewardVerify();
}
