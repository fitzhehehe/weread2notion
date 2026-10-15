#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
exec python3 server/app.py --host 127.0.0.1 --port 8765
