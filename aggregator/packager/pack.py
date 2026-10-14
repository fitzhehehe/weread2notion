#!/usr/bin/env python3
"""把后台配置写成渠道包里的 aggregator.json。不往 APK 里塞渠道 SDK。"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.parse
import urllib.request

ADAPTERS = {
    "test": "com.aggregator.test.TestChannelAdapter",
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--app-id", required=True)
    parser.add_argument("--channel", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--server", default="")
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--adapter-class", default="")
    parser.add_argument("--placement", action="append", default=[])
    parser.add_argument("--from-server", default="")
    args = parser.parse_args()

    adapter = args.adapter_class or ADAPTERS.get(args.channel, "")
    if not adapter:
        print(f"{args.channel} 还没有适配器。官方 aar 接入后用 --adapter-class 指定类名。", file=sys.stderr)
        sys.exit(2)

    placements = {}
    for item in args.placement:
        placement_id, slot = item.split("=", 1)
        placements[placement_id] = slot
    server_reward = False
    if args.from_server:
        query = urllib.parse.urlencode({"appId": args.app_id, "channelCode": args.channel})
        with urllib.request.urlopen(args.from_server.rstrip("/") + "/v1/config?" + query, timeout=5) as response:
            remote = json.load(response)
        placements.update(remote.get("placements", {}))
        server_reward = bool(remote.get("serverReward"))

    server = "" if args.offline else args.server
    payload = {
        "appId": args.app_id,
        "channelCode": args.channel,
        "serverBaseUrl": server,
        "serverReward": server_reward and not args.offline,
        "adapterClass": adapter,
        "placements": placements,
    }
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(args.out)


if __name__ == "__main__":
    main()
