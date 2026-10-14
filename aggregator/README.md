# 渠道聚合 SDK

游戏只调用这一套接口。登录由打进渠道包的渠道 SDK 完成，不经过发行服务器。单机不部署服务器也可以登录。发行服务器只负责商品金额、渠道支付通知和后台配置，不保存游戏存档。

当前仓库里能跑通的是测试渠道 `test`。OPPO、vivo、华为的官方 aar 还没有放进来；名单到了以后只新增适配器，不改游戏调用。

## 跑测试

```sh
sh aggregator/scripts/test.sh
```

## 打开后台

```sh
sh aggregator/scripts/serve.sh
```

浏览器打开 http://127.0.0.1:8765/admin

## 打出一份单机测试配置

```sh
python3 aggregator/packager/pack.py \
  --app-id g1 --channel test --offline \
  --placement reward_main=0 \
  --out aggregator.json
```

把生成的 `aggregator.json` 放到 Android 工程的 `assets/`。游戏工程同时编译 `aggregator/sdk` 和 `aggregator/android`。

## 文档

- [游戏接入](docs/游戏接入.md)
- [游戏服与支付](docs/游戏服与支付.md)
- [后台和打包](docs/后台和打包.md)
- [新增渠道适配器](docs/新增渠道适配器.md)
