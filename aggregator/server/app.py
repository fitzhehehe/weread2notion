#!/usr/bin/env python3
"""发行服务。登录不经过这里。单机存档不进这张库。"""

from __future__ import annotations

import hashlib
import hmac
import json
import secrets
import sqlite3
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parent
LOCK = threading.Lock()
CONN: sqlite3.Connection | None = None
SKEW_SECONDS = 300
AD_TYPES = {"reward", "interstitial", "splash", "banner"}
TABLES = {
    "games",
    "channel_bindings",
    "products",
    "placements",
    "orders",
    "notify_retries",
    "reward_completions",
}

SCHEMA = """
CREATE TABLE IF NOT EXISTS games (
  app_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  app_key TEXT NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('offline', 'online')),
  pay_notify_url TEXT NOT NULL DEFAULT '',
  reward_notify_url TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS channel_bindings (
  app_id TEXT NOT NULL,
  channel_code TEXT NOT NULL,
  channel_app_id TEXT NOT NULL DEFAULT '',
  package_name TEXT NOT NULL DEFAULT '',
  channel_secret TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (app_id, channel_code)
);
CREATE TABLE IF NOT EXISTS products (
  app_id TEXT NOT NULL,
  product_id TEXT NOT NULL,
  name TEXT NOT NULL,
  amount_fen INTEGER NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (app_id, product_id)
);
CREATE TABLE IF NOT EXISTS placements (
  app_id TEXT NOT NULL,
  placement_id TEXT NOT NULL,
  ad_type TEXT NOT NULL,
  channel_code TEXT NOT NULL,
  channel_slot_id TEXT NOT NULL,
  PRIMARY KEY (app_id, placement_id, channel_code)
);
CREATE TABLE IF NOT EXISTS orders (
  order_id TEXT PRIMARY KEY,
  app_id TEXT NOT NULL,
  channel_code TEXT NOT NULL,
  game_order_id TEXT NOT NULL,
  channel_user_id TEXT NOT NULL,
  product_id TEXT NOT NULL,
  amount_fen INTEGER NOT NULL,
  role_id TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(app_id, game_order_id)
);
CREATE TABLE IF NOT EXISTS notify_retries (
  order_id TEXT PRIMARY KEY,
  attempts INTEGER NOT NULL,
  last_error TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS reward_completions (
  play_id TEXT PRIMARY KEY,
  app_id TEXT NOT NULL,
  channel_code TEXT NOT NULL,
  channel_user_id TEXT NOT NULL,
  placement_id TEXT NOT NULL,
  created_at TEXT NOT NULL
);
"""


def now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def sign(secret: str, *parts: str) -> str:
    msg = "|".join(parts).encode()
    return hmac.new(secret.encode(), msg, hashlib.sha256).hexdigest()


def connect(path: str) -> sqlite3.Connection:
    global CONN
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    CONN = sqlite3.connect(path, check_same_thread=False)
    CONN.row_factory = sqlite3.Row
    CONN.executescript(SCHEMA)
    return CONN


def db() -> sqlite3.Connection:
    if CONN is None:
        raise RuntimeError("数据库未打开")
    return CONN


def one(sql: str, args: tuple = ()) -> sqlite3.Row | None:
    return db().execute(sql, args).fetchone()


def rows(sql: str, args: tuple = ()) -> list[dict]:
    return [dict(row) for row in db().execute(sql, args).fetchall()]


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        try:
            if parsed.path in ("/admin", "/admin/"):
                self._file(ROOT / "static" / "admin.html", "text/html; charset=utf-8")
            elif parsed.path == "/health":
                self._json({"ok": True})
            elif parsed.path == "/api/games":
                self._json(rows("SELECT app_id, name, kind, pay_notify_url, reward_notify_url, created_at FROM games ORDER BY created_at"))
            elif parsed.path.startswith("/api/games/"):
                app_id = parsed.path.removeprefix("/api/games/")
                self._json(self._game_detail(app_id))
            elif parsed.path == "/api/orders":
                query = parse_qs(parsed.query)
                app_id = query.get("appId", [""])[0]
                if app_id:
                    self._json(rows("SELECT * FROM orders WHERE app_id = ? ORDER BY created_at DESC", (app_id,)))
                else:
                    self._json(rows("SELECT * FROM orders ORDER BY created_at DESC"))
            elif parsed.path == "/v1/config":
                self._json(self._config(parse_qs(parsed.query)))
            else:
                raise AppError(404, "没有这个地址")
        except AppError as exc:
            self._send_error(exc.status, exc.message)

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        body = self._body()
        path = parsed.path
        try:
            if path == "/api/games":
                self._json(self._create_game(body), 201)
            elif path.startswith("/api/games/") and path.endswith("/products"):
                app_id = path.removeprefix("/api/games/").removesuffix("/products").strip("/")
                self._json(self._add_product(app_id, body), 201)
            elif path.startswith("/api/games/") and path.endswith("/channels"):
                app_id = path.removeprefix("/api/games/").removesuffix("/channels").strip("/")
                self._json(self._add_channel(app_id, body), 201)
            elif path.startswith("/api/games/") and path.endswith("/placements"):
                app_id = path.removeprefix("/api/games/").removesuffix("/placements").strip("/")
                self._json(self._add_placement(app_id, body), 201)
            elif path.startswith("/api/orders/") and path.endswith("/renotify"):
                order_id = path.removeprefix("/api/orders/").removesuffix("/renotify").strip("/")
                self._json(self._renotify(order_id))
            elif path == "/v1/orders":
                self._json(self._create_order(body), 201)
            elif path.startswith("/v1/channels/") and path.endswith("/pay-notify"):
                channel = path.removeprefix("/v1/channels/").removesuffix("/pay-notify").strip("/")
                self._json(self._pay_notify(channel, body))
            elif path == "/v1/reward-completions":
                self._json(self._reward(body), 201)
            else:
                raise AppError(404, "没有这个地址")
        except AppError as exc:
            self._send_error(exc.status, exc.message)

    def log_message(self, fmt: str, *args) -> None:
        return

    def _game_detail(self, app_id: str) -> dict:
        game = one("SELECT * FROM games WHERE app_id = ?", (app_id,))
        if game is None:
            raise AppError(404, "游戏不存在")
        return {
            "game": dict(game),
            "products": rows("SELECT * FROM products WHERE app_id = ?", (app_id,)),
            "channels": rows("SELECT * FROM channel_bindings WHERE app_id = ?", (app_id,)),
            "placements": rows("SELECT * FROM placements WHERE app_id = ?", (app_id,)),
            "orders": rows("SELECT * FROM orders WHERE app_id = ? ORDER BY created_at DESC", (app_id,)),
        }

    def _create_game(self, body: dict) -> dict:
        name = str(body.get("name", "")).strip()
        kind = body.get("kind", "offline")
        if not name:
            raise AppError(400, "缺少游戏名")
        if kind not in ("offline", "online"):
            raise AppError(400, "kind 只能是 offline 或 online")
        app_id = "g_" + secrets.token_hex(4)
        app_key = secrets.token_hex(16)
        with LOCK:
            db().execute(
                "INSERT INTO games(app_id, name, app_key, kind, pay_notify_url, reward_notify_url, created_at) VALUES(?,?,?,?,?,?,?)",
                (app_id, name, app_key, kind, str(body.get("payNotifyUrl", "")), str(body.get("rewardNotifyUrl", "")), now()),
            )
            db().commit()
        return {"appId": app_id, "appKey": app_key, "name": name, "kind": kind}

    def _add_product(self, app_id: str, body: dict) -> dict:
        self._require_game(app_id)
        product_id = str(body.get("productId", "")).strip()
        name = str(body.get("name", "")).strip()
        amount = int(body.get("amountFen", 0))
        if not product_id or not name or amount <= 0:
            raise AppError(400, "商品需要 productId、name 和大于 0 的 amountFen")
        with LOCK:
            db().execute(
                "INSERT INTO products(app_id, product_id, name, amount_fen, enabled) VALUES(?,?,?,?,1) "
                "ON CONFLICT(app_id, product_id) DO UPDATE SET name=excluded.name, amount_fen=excluded.amount_fen, enabled=1",
                (app_id, product_id, name, amount),
            )
            db().commit()
        return {"appId": app_id, "productId": product_id, "name": name, "amountFen": amount}

    def _add_channel(self, app_id: str, body: dict) -> dict:
        self._require_game(app_id)
        channel = str(body.get("channelCode", "")).strip()
        secret = str(body.get("channelSecret", "")).strip()
        if not channel or not secret:
            raise AppError(400, "渠道需要 channelCode 和 channelSecret")
        with LOCK:
            db().execute(
                "INSERT INTO channel_bindings(app_id, channel_code, channel_app_id, package_name, channel_secret, enabled) "
                "VALUES(?,?,?,?,?,1) ON CONFLICT(app_id, channel_code) DO UPDATE SET "
                "channel_app_id=excluded.channel_app_id, package_name=excluded.package_name, channel_secret=excluded.channel_secret, enabled=1",
                (app_id, channel, str(body.get("channelAppId", "")), str(body.get("packageName", "")), secret),
            )
            db().commit()
        return {"appId": app_id, "channelCode": channel}

    def _add_placement(self, app_id: str, body: dict) -> dict:
        self._require_game(app_id)
        placement_id = str(body.get("placementId", "")).strip()
        ad_type = str(body.get("adType", "")).strip()
        channel = str(body.get("channelCode", "")).strip()
        slot = str(body.get("channelSlotId", "")).strip()
        if ad_type not in AD_TYPES or not placement_id or not channel or slot == "":
            raise AppError(400, "广告位需要 placementId、adType、channelCode、channelSlotId")
        with LOCK:
            db().execute(
                "INSERT INTO placements(app_id, placement_id, ad_type, channel_code, channel_slot_id) VALUES(?,?,?,?,?) "
                "ON CONFLICT(app_id, placement_id, channel_code) DO UPDATE SET ad_type=excluded.ad_type, channel_slot_id=excluded.channel_slot_id",
                (app_id, placement_id, ad_type, channel, slot),
            )
            db().commit()
        return {"placementId": placement_id, "channelSlotId": slot}

    def _create_order(self, body: dict) -> dict:
        app_id = str(body.get("appId", ""))
        game = self._require_game(app_id)
        channel = str(body.get("channelCode", ""))
        user = str(body.get("channelUserId", ""))
        product_id = str(body.get("productId", ""))
        game_order_id = str(body.get("gameOrderId", ""))
        role_id = str(body.get("roleId", ""))
        if not channel or not user or not product_id or not game_order_id:
            raise AppError(400, "建单缺少 channelCode、channelUserId、productId 或 gameOrderId")
        provided = str(body.get("sign", ""))
        if provided:
            timestamp = str(body.get("timestamp", ""))
            self._check_time(timestamp)
            expected = sign(game["app_key"], app_id, game_order_id, channel, user, product_id, role_id, timestamp)
            if not hmac.compare_digest(expected, provided):
                raise AppError(401, "签名不对")
        binding = one(
            "SELECT * FROM channel_bindings WHERE app_id = ? AND channel_code = ? AND enabled = 1",
            (app_id, channel),
        )
        if binding is None:
            raise AppError(400, "渠道未启用")
        product = one(
            "SELECT * FROM products WHERE app_id = ? AND product_id = ? AND enabled = 1",
            (app_id, product_id),
        )
        if product is None:
            raise AppError(404, "商品不存在")
        existing = one("SELECT * FROM orders WHERE app_id = ? AND game_order_id = ?", (app_id, game_order_id))
        if existing is not None:
            same = (
                existing["product_id"] == product_id
                and existing["channel_code"] == channel
                and existing["channel_user_id"] == user
            )
            if not same:
                raise AppError(409, "游戏订单号已用于另一笔订单")
            return order_view(existing)
        order_id = "o_" + secrets.token_hex(8)
        with LOCK:
            db().execute(
                "INSERT INTO orders(order_id, app_id, channel_code, game_order_id, channel_user_id, product_id, amount_fen, role_id, status, created_at) "
                "VALUES(?,?,?,?,?,?,?,?, 'created', ?)",
                (order_id, app_id, channel, game_order_id, user, product_id, int(product["amount_fen"]), role_id, now()),
            )
            db().commit()
        created = one("SELECT * FROM orders WHERE order_id = ?", (order_id,))
        return order_view(created)

    def _pay_notify(self, channel: str, body: dict) -> dict:
        app_id = str(body.get("appId", ""))
        order_id = str(body.get("orderId", ""))
        amount = int(body.get("amountFen", -1))
        timestamp = str(body.get("timestamp", ""))
        provided = str(body.get("sign", ""))
        self._check_time(timestamp)
        order = one("SELECT * FROM orders WHERE order_id = ?", (order_id,))
        if order is None or order["app_id"] != app_id:
            raise AppError(404, "订单不存在")
        if order["channel_code"] != channel:
            raise AppError(400, "渠道和订单不一致")
        binding = one(
            "SELECT * FROM channel_bindings WHERE app_id = ? AND channel_code = ?",
            (app_id, channel),
        )
        if binding is None:
            raise AppError(400, "渠道未配置")
        expected = sign(binding["channel_secret"], app_id, order_id, str(amount), timestamp)
        if not hmac.compare_digest(expected, provided):
            raise AppError(401, "签名不对")
        if amount != int(order["amount_fen"]):
            raise AppError(400, "金额和商品不一致")
        if order["status"] == "notified":
            return {"status": "notified", "duplicate": True}
        with LOCK:
            db().execute("UPDATE orders SET status = 'paid' WHERE order_id = ?", (order_id,))
            db().commit()
        return self._deliver(order_id)

    def _renotify(self, order_id: str) -> dict:
        order = one("SELECT * FROM orders WHERE order_id = ?", (order_id,))
        if order is None:
            raise AppError(404, "订单不存在")
        if order["status"] == "created":
            raise AppError(409, "尚未支付")
        if order["status"] == "notified":
            return {"status": "notified", "duplicate": True}
        return self._deliver(order_id)

    def _deliver(self, order_id: str) -> dict:
        order = one("SELECT * FROM orders WHERE order_id = ?", (order_id,))
        game = self._require_game(order["app_id"])
        url = game["pay_notify_url"]
        if not url:
            return {"status": "paid", "delivered": False}
        timestamp = str(int(time.time()))
        payload = {
            "orderId": order["order_id"],
            "gameOrderId": order["game_order_id"],
            "appId": order["app_id"],
            "productId": order["product_id"],
            "amountFen": int(order["amount_fen"]),
            "channelCode": order["channel_code"],
            "channelUserId": order["channel_user_id"],
            "roleId": order["role_id"],
            "timestamp": timestamp,
            "sign": sign(
                game["app_key"],
                order["order_id"],
                order["game_order_id"],
                order["product_id"],
                str(order["amount_fen"]),
                order["channel_user_id"],
                timestamp,
            ),
        }
        try:
            status, raw = post_json(url, payload)
            parsed = json.loads(raw.decode() or "{}")
            if status != 200 or parsed.get("ok") is not True:
                raise AppError(502, "游戏服未确认发货")
        except AppError:
            self._remember_failure(order_id, "游戏服未确认发货")
            raise
        except Exception as exc:
            self._remember_failure(order_id, str(exc))
            raise AppError(502, "通知游戏服失败") from exc
        with LOCK:
            db().execute("UPDATE orders SET status = 'notified' WHERE order_id = ?", (order_id,))
            db().execute("DELETE FROM notify_retries WHERE order_id = ?", (order_id,))
            db().commit()
        return {"status": "notified", "duplicate": False}

    def _remember_failure(self, order_id: str, message: str) -> None:
        with LOCK:
            db().execute("UPDATE orders SET status = 'notify_fail' WHERE order_id = ?", (order_id,))
            current = one("SELECT attempts FROM notify_retries WHERE order_id = ?", (order_id,))
            attempts = 1 if current is None else int(current["attempts"]) + 1
            db().execute(
                "INSERT INTO notify_retries(order_id, attempts, last_error, updated_at) VALUES(?,?,?,?) "
                "ON CONFLICT(order_id) DO UPDATE SET attempts=excluded.attempts, last_error=excluded.last_error, updated_at=excluded.updated_at",
                (order_id, attempts, message[:500], now()),
            )
            db().commit()

    def _reward(self, body: dict) -> dict:
        app_id = str(body.get("appId", ""))
        game = self._require_game(app_id)
        if game["kind"] != "online":
            raise AppError(409, "单机不记录激励")
        play_id = str(body.get("playId", "")).strip()
        placement_id = str(body.get("placementId", "")).strip()
        channel = str(body.get("channelCode", "")).strip()
        user = str(body.get("channelUserId", "")).strip()
        if not play_id or not placement_id or not channel or not user:
            raise AppError(400, "激励完成单缺少字段")
        with LOCK:
            db().execute(
                "INSERT OR IGNORE INTO reward_completions(play_id, app_id, channel_code, channel_user_id, placement_id, created_at) "
                "VALUES(?,?,?,?,?,?)",
                (play_id, app_id, channel, user, placement_id, now()),
            )
            db().commit()
        return {"playId": play_id}

    def _config(self, query: dict) -> dict:
        app_id = query.get("appId", [""])[0]
        channel = query.get("channelCode", [""])[0]
        game = one("SELECT kind FROM games WHERE app_id = ?", (app_id,))
        if game is None:
            raise AppError(404, "游戏不存在")
        found = rows(
            "SELECT placement_id, channel_slot_id FROM placements WHERE app_id = ? AND channel_code = ?",
            (app_id, channel),
        )
        placements = {row["placement_id"]: row["channel_slot_id"] for row in found}
        return {
            "appId": app_id,
            "channelCode": channel,
            "serverReward": game["kind"] == "online",
            "placements": placements,
        }

    def _require_game(self, app_id: str) -> sqlite3.Row:
        game = one("SELECT * FROM games WHERE app_id = ?", (app_id,))
        if game is None:
            raise AppError(404, "游戏不存在")
        return game

    def _check_time(self, timestamp: str) -> None:
        try:
            value = int(timestamp)
        except ValueError as exc:
            raise AppError(400, "timestamp 无效") from exc
        if abs(int(time.time()) - value) > SKEW_SECONDS:
            raise AppError(400, "timestamp 超出范围")

    def _body(self) -> dict:
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            parsed = json.loads(raw.decode() or "{}")
        except json.JSONDecodeError as exc:
            raise AppError(400, "JSON 无效") from exc
        if not isinstance(parsed, dict):
            raise AppError(400, "需要 JSON 对象")
        return parsed

    def _file(self, path: Path, content_type: str) -> None:
        data = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _json(self, payload: dict | list, status: int = 200) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _send_error(self, status: int, message: str) -> None:
        data = json.dumps({"error": message}, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class AppError(Exception):
    def __init__(self, status: int, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.message = message


def order_view(row: sqlite3.Row) -> dict:
    return {
        "orderId": row["order_id"],
        "appId": row["app_id"],
        "channelCode": row["channel_code"],
        "gameOrderId": row["game_order_id"],
        "channelUserId": row["channel_user_id"],
        "productId": row["product_id"],
        "amountFen": int(row["amount_fen"]),
        "roleId": row["role_id"],
        "status": row["status"],
    }


def post_json(url: str, payload: dict) -> tuple[int, bytes]:
    data = json.dumps(payload).encode()
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=3) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--db", default=str(ROOT / "data" / "aggregator.sqlite"))
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    connect(args.db)
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"admin http://{args.host}:{args.port}/admin")
    server.serve_forever()


if __name__ == "__main__":
    main()
