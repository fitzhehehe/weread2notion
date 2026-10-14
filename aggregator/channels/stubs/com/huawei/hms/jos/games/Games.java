package com.huawei.hms.jos.games;

import android.app.Activity;

public class Games {
    public static BuoyClient getBuoyClient(Activity activity) { return new BuoyClient(); }
    public static PlayersClient getPlayersClient(Activity activity) { return new PlayersClient(); }
}
