package com.vivo.mobilead.unified.base.callback;

import com.vivo.mobilead.unified.base.VivoAdError;

public interface MediaListener {
    void onVideoStart();
    void onVideoPause();
    void onVideoPlay();
    void onVideoError(VivoAdError error);
    void onVideoCompletion();
    void onVideoCached();
}
