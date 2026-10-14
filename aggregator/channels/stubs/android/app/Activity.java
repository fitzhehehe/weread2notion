package android.app;

import android.content.Context;
import android.content.Intent;

public class Activity extends Context {
    public void runOnUiThread(Runnable action) {
        action.run();
    }

    public void startActivityForResult(Intent intent, int requestCode) {}

    public void finish() {}
}
