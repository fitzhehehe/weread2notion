package com.aggregator.android;

import android.app.Activity;
import android.content.Intent;
import com.aggregator.sdk.ActivityResults;
import com.aggregator.sdk.EngineEvents;
import com.aggregator.sdk.Host;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 由游戏 Gradle 和 android.jar 一起编译。逻辑在 EngineEvents，这里只读母包里的 aggregator.json。 */
public final class AggregatorBridge {
    private AggregatorBridge() {}

    public static void start(Activity activity) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                activity.getAssets().open("aggregator.json"), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                json.append(line);
            }
            Host.setActivity(activity);
            EngineEvents.start(json.toString());
        } catch (Exception e) {
            EngineEvents.reset();
            EngineEvents.start("{\"appId\":\"\",\"channelCode\":\"\",\"adapterClass\":\"\"}");
        }
    }

    public static void login() {
        EngineEvents.login();
    }

    public static void submitRole(
            String type, String roleId, String roleName, int roleLevel, String serverId, String serverName) {
        EngineEvents.submitRole(type, roleId, roleName, roleLevel, serverId, serverName);
    }

    public static void pay(String orderId, String productId, String gameOrderId, String channelUserId, String roleId) {
        EngineEvents.pay(orderId, productId, gameOrderId, channelUserId, roleId);
    }

    public static void loadAd(String adType, String placementId) {
        EngineEvents.loadAd(adType, placementId);
    }

    public static void showAd(String adType, String placementId) {
        EngineEvents.showAd(adType, placementId);
    }

    public static String poll() {
        String event = EngineEvents.poll();
        return event == null ? "" : event;
    }

    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        ActivityResults.dispatch(requestCode, resultCode, data);
    }
}
