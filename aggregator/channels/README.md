# 渠道适配器

| 渠道编码 | 类 | 登录和支付 | 广告 |
| --- | --- | --- | --- |
| oppo | `com.aggregator.channel.oppo.OppoAdapter` | OPPO 游戏中心 `GameCenterSDK` | 不包含广告联盟 |
| vivo | `com.aggregator.channel.vivo.VivoAdapter` | `VivoUnionSDK` | 激励视频用广告联盟 `UnifiedVivoRewardVideoAd` |
| huawei | `com.aggregator.channel.huawei.HuaweiAdapter` | 华为应用市场 HMS 账号、游戏和 IAP | 不包含华为广告 |
| xiaomi | `com.aggregator.channel.xiaomi.XiaomiAdapter` | 小米游戏联运 `MiCommplatform` | 不包含米盟广告 |

`channels/stubs` 只用于编译检查，方法是空的。打渠道包时用官方 aar 替换同名类，不要把 stubs 打进去。

游戏 Activity 要调用 `AggregatorBridge.onActivityResult`，华为登录和支付才收得回结果。

渠道参数写在 `aggregator.json` 的 `channelParams`：

- OPPO：`appSecret`、`notifyUrl`
- vivo：`channelAppId`、`notifyUrl`。每笔支付的 `paySignature` 由服务端计算后传给 `pay`
- 华为：商品 ID 使用华为后台的商品 ID
- 小米：金额必须是整元。按元调用 `setAmount`

小米现行游戏联运文档没有单独的角色上报接口，所以角色调用只返回成功。华为同样没有放进这个适配器的角色接口。
