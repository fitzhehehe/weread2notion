using System.Collections;
using UnityEngine;

/// 游戏 Activity 启动时调用 AggregatorBridge.start。脚本只轮询事件。
public class AggregatorSample : MonoBehaviour
{
    AndroidJavaClass bridge;

    void Start()
    {
        bridge = new AndroidJavaClass("com.aggregator.android.AggregatorBridge");
        StartCoroutine(Run());
    }

    IEnumerator Run()
    {
        bridge.CallStatic("login");
        yield return Wait("login_ok");
        bridge.CallStatic("submitRole", "create", "r1", "角色", 1, "1", "一区");
        yield return Wait("role_ok");
        bridge.CallStatic("pay", "", "gem_6", "local-1", "test_user_001", "r1");
        string pay = "";
        yield return WaitRaw("pay_ok", value => pay = value);
        Debug.Log(pay);
        if (pay.Contains("\"deliverable\":true"))
        {
            Debug.LogError("客户端支付回调不能发货");
        }
        bridge.CallStatic("loadAd", "reward", "reward_main");
        yield return WaitRaw("onLoaded", value => { });
        bridge.CallStatic("showAd", "reward", "reward_main");
    }

    IEnumerator Wait(string name)
    {
        yield return WaitRaw(name, value => Debug.Log(value));
    }

    IEnumerator WaitRaw(string token, System.Action<string> done)
    {
        while (true)
        {
            string raw = bridge.CallStatic<string>("poll");
            if (!string.IsNullOrEmpty(raw) && raw.Contains(token))
            {
                done(raw);
                yield break;
            }
            yield return null;
        }
    }
}
