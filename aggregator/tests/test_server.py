import json
import os
import sqlite3
import subprocess
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "server"
sys_path = str(SERVER)
import sys

sys.path.insert(0, sys_path)
import app as publisher  # noqa: E402


class GameServer(BaseHTTPRequestHandler):
    bodies = []

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        GameServer.bodies.append(self.rfile.read(length))
        data = b'{"ok":true}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        return


class PublisherTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        publisher.connect(os.path.join(self.tmp.name, "t.sqlite"))
        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), publisher.Handler)
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.httpd.server_address[1]}"
        GameServer.bodies = []

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        publisher.db().close()
        self.tmp.cleanup()

    def test_schema_has_no_save_or_login_log(self):
        names = {
            row[0]
            for row in publisher.db().execute("SELECT name FROM sqlite_master WHERE type='table'")
            if not str(row[0]).startswith("sqlite_")
        }
        self.assertEqual(names, publisher.TABLES)
        self.assertFalse(names & {"saves", "sessions", "login_logs", "roles"})

    def test_offline_login_is_not_a_server_route_and_reward_is_rejected(self):
        game = self.post("/api/games", {"name": "单机", "kind": "offline"})
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.post("/v1/login", {"appId": game["appId"]})
        self.assertEqual(caught.exception.code, 404)
        with self.assertRaises(urllib.error.HTTPError) as reward:
            self.post(
                "/v1/reward-completions",
                {
                    "appId": game["appId"],
                    "channelCode": "test",
                    "channelUserId": "u",
                    "placementId": "reward_main",
                    "playId": "p1",
                },
            )
        self.assertEqual(reward.exception.code, 409)
        count = publisher.db().execute("SELECT COUNT(*) FROM reward_completions").fetchone()[0]
        self.assertEqual(count, 0)

    def test_order_amount_comes_from_product_and_notify_is_idempotent(self):
        game_server = ThreadingHTTPServer(("127.0.0.1", 0), GameServer)
        threading.Thread(target=game_server.serve_forever, daemon=True).start()
        notify = f"http://127.0.0.1:{game_server.server_address[1]}/pay"
        game = self.post(
            "/api/games",
            {"name": "有内购", "kind": "offline", "payNotifyUrl": notify},
        )
        self.post(
            f"/api/games/{game['appId']}/products",
            {"productId": "gem_6", "name": "60钻", "amountFen": 600},
        )
        self.post(
            f"/api/games/{game['appId']}/channels",
            {"channelCode": "test", "channelSecret": "channel-sec", "packageName": "com.demo"},
        )
        created = self.post(
            "/v1/orders",
            {
                "appId": game["appId"],
                "channelCode": "test",
                "channelUserId": "test_user_001",
                "productId": "gem_6",
                "gameOrderId": "local-1",
                "roleId": "r1",
                "amountFen": 1,
            },
        )
        self.assertEqual(created["amountFen"], 600)
        self.assertEqual(created["status"], "created")
        timestamp = str(int(time.time()))
        bad = dict(created)
        with self.assertRaises(urllib.error.HTTPError):
            self.post(
                "/v1/channels/test/pay-notify",
                {
                    "appId": game["appId"],
                    "orderId": created["orderId"],
                    "amountFen": 1,
                    "timestamp": timestamp,
                    "sign": publisher.sign("channel-sec", game["appId"], created["orderId"], "1", timestamp),
                },
            )
        paid = self.post(
            "/v1/channels/test/pay-notify",
            {
                "appId": game["appId"],
                "orderId": created["orderId"],
                "amountFen": 600,
                "timestamp": timestamp,
                "sign": publisher.sign("channel-sec", game["appId"], created["orderId"], "600", timestamp),
            },
        )
        self.assertEqual(paid["status"], "notified")
        again = self.post(
            "/v1/channels/test/pay-notify",
            {
                "appId": game["appId"],
                "orderId": created["orderId"],
                "amountFen": 600,
                "timestamp": timestamp,
                "sign": publisher.sign("channel-sec", game["appId"], created["orderId"], "600", timestamp),
            },
        )
        self.assertTrue(again["duplicate"])
        self.assertEqual(len(GameServer.bodies), 1)
        notice = json.loads(GameServer.bodies[0])
        self.assertEqual(notice["amountFen"], 600)
        game_server.shutdown()
        game_server.server_close()

    def test_packager_offline_omits_server_and_unknown_channel_fails(self):
        out = os.path.join(self.tmp.name, "aggregator.json")
        subprocess.check_call(
            [
                sys.executable,
                str(ROOT / "packager" / "pack.py"),
                "--app-id",
                "g1",
                "--channel",
                "test",
                "--offline",
                "--server",
                "http://127.0.0.1:9",
                "--placement",
                "reward_main=0",
                "--out",
                out,
            ]
        )
        payload = json.loads(Path(out).read_text())
        self.assertEqual(payload["serverBaseUrl"], "")
        self.assertEqual(payload["adapterClass"], "com.aggregator.test.TestChannelAdapter")
        self.assertEqual(payload["placements"]["reward_main"], "0")
        for channel, class_name in {
            "oppo": "com.aggregator.channel.oppo.OppoAdapter",
            "vivo": "com.aggregator.channel.vivo.VivoAdapter",
            "huawei": "com.aggregator.channel.huawei.HuaweiAdapter",
            "xiaomi": "com.aggregator.channel.xiaomi.XiaomiAdapter",
        }.items():
            subprocess.check_call(
                [
                    sys.executable,
                    str(ROOT / "packager" / "pack.py"),
                    "--app-id",
                    "g1",
                    "--channel",
                    channel,
                    "--offline",
                    "--param",
                    "channelAppId=store-app",
                    "--out",
                    out,
                ]
            )
            packed = json.loads(Path(out).read_text())
            self.assertEqual(packed["adapterClass"], class_name)
            self.assertEqual(packed["channelParams"]["channelAppId"], "store-app")
        result = subprocess.run(
            [sys.executable, str(ROOT / "packager" / "pack.py"), "--app-id", "g1", "--channel", "bilibili", "--offline", "--out", out],
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 2)
        self.assertIn("还没有适配器", result.stderr)

    def test_java_sdk_against_server(self):
        classes = ROOT / "sdk" / "build" / "classes"
        tests = ROOT / "sdk" / "build" / "test"
        if not (tests / "com" / "aggregator" / "sdk" / "IntegrationTest.class").exists():
            self.skipTest("javac 还没跑")
        game = self.post("/api/games", {"name": "联调", "kind": "offline"})
        self.post(
            f"/api/games/{game['appId']}/products",
            {"productId": "gem_6", "name": "60钻", "amountFen": 600},
        )
        self.post(
            f"/api/games/{game['appId']}/channels",
            {"channelCode": "test", "channelSecret": "channel-sec"},
        )
        self.post(
            f"/api/games/{game['appId']}/placements",
            {"placementId": "reward_main", "adType": "reward", "channelCode": "test", "channelSlotId": "0"},
        )
        proc = subprocess.run(
            ["java", "-cp", f"{classes}:{tests}", "com.aggregator.sdk.IntegrationTest", self.base, game["appId"]],
            capture_output=True,
            text=True,
        )
        self.assertEqual(proc.returncode, 0, proc.stderr + proc.stdout)
        orders = publisher.rows("SELECT * FROM orders")
        self.assertEqual(len(orders), 1)
        self.assertEqual(orders[0]["amount_fen"], 600)
        self.assertEqual(orders[0]["status"], "created")
        self.assertEqual(publisher.db().execute("SELECT COUNT(*) FROM reward_completions").fetchone()[0], 0)

    def test_vivo_signature_matches_official_sample_and_form_notify(self):
        sample = {
            "appId": "123",
            "cpOrderNumber": "123456878",
            "extInfo": "扩展参数",
            "notifyUrl": "http://127.0.0.1:8080/vivopay/vivoPay/testNotify",
            "orderAmount": "1000",
            "productDesc": "vivo Xplay",
            "productName": "手机",
            "balance": "用户余额",
            "vip": "用户vip等级",
            "level": "用户角色等级",
            "party": "工会",
            "roleId": "角色ID",
            "roleName": "角色名",
            "serverName": "区服信息",
        }
        self.assertEqual(publisher.vivo_sign(sample, "123456789123456"), "07e4198e86717626a6cbfdf1996c9e4a")
        game = self.post("/api/games", {"name": "vivo", "kind": "offline"})
        self.post(
            f"/api/games/{game['appId']}/products",
            {"productId": "gem_6", "name": "60钻", "amountFen": 600},
        )
        self.post(
            f"/api/games/{game['appId']}/channels",
            {
                "channelCode": "vivo",
                "channelAppId": "vivo-app",
                "channelSecret": "vivo-key",
                "notifyUrl": "https://pay.example/vivo",
            },
        )
        created = self.post(
            "/v1/orders",
            {
                "appId": game["appId"],
                "channelCode": "vivo",
                "channelUserId": "openid-1",
                "productId": "gem_6",
                "gameOrderId": "local-vivo",
                "roleId": "r1",
            },
        )
        fields = {
            "appId": "vivo-app",
            "cpOrderNumber": created["orderId"],
            "notifyUrl": "https://pay.example/vivo",
            "orderAmount": "600",
            "productName": "gem_6",
            "productDesc": "gem_6",
            "roleId": "r1",
        }
        self.assertEqual(created["vivoSignature"], publisher.vivo_sign(fields, "vivo-key"))
        fields.update(
            {
                "respCode": "200",
                "respMsg": "交易完成",
                "tradeType": "01",
                "tradeStatus": "0000",
                "orderNumber": "vivo-order",
                "uid": "openid-1",
            }
        )
        fields["signature"] = publisher.vivo_sign(fields, "vivo-key")
        body = urllib.parse.urlencode(fields).encode()
        request = urllib.request.Request(
            self.base + "/v1/channels/vivo/pay-notify",
            data=body,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        with urllib.request.urlopen(request) as response:
            self.assertEqual(response.read().decode(), "success")
        status = publisher.one("SELECT status FROM orders WHERE order_id = ?", (created["orderId"],))
        self.assertEqual(status["status"], "paid")

    def post(self, path, payload):
        data = json.dumps(payload).encode()
        request = urllib.request.Request(
            self.base + path, data=data, headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(request) as response:
            return json.load(response)


if __name__ == "__main__":
    unittest.main()
