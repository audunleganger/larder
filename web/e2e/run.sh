#!/usr/bin/env bash
# Builds the server and web GUI, starts the server on a throwaway data directory,
# and runs the browser walkthrough (e2e/flow.mjs) against it.
#   CHROMIUM_PATH=/path/to/chromium web/e2e/run.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PORT="${E2E_PORT:-18090}"
DATA="$(mktemp -d)"

(cd "$ROOT" && ./gradlew -q :server:buildFatJar)
(cd "$ROOT/web" && npm run build >/dev/null)

# Photo links point at the walkthrough's own image server on localhost.
CC_PHOTO_FETCH_ALLOW_PRIVATE=true CC_PORT="$PORT" CC_DATA_DIR="$DATA" CC_WEB_DIR="$ROOT/web/dist" java -jar "$ROOT/server/build/libs/calorie-companion-server.jar" >"$DATA/server.log" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null; rm -rf "$DATA"' EXIT
for _ in $(seq 60); do curl -sf "http://localhost:$PORT/api/health" >/dev/null && break; sleep 0.5; done

E2E_BASE_URL="http://localhost:$PORT" node "$ROOT/web/e2e/flow.mjs"
