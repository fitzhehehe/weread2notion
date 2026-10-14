package com.huawei.hms.jos.games;

import android.app.Activity;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.jos.games.player.Player;

public class PlayersClient {
    public Task<Player> getGamePlayer() { return new Task<>(); }
}
