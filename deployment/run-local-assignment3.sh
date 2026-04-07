#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${LOG_DIR:-$ROOT_DIR/deployment/logs/local}"
SERVER_URL="${SERVER_URL:-ws://localhost:8081}"
SERVER_HEALTH_URL="${SERVER_HEALTH_URL:-http://localhost:8080/health}"
METRICS_HEALTH_URL="${METRICS_HEALTH_URL:-http://localhost:8083/health}"
SKIP_BUILD="${SKIP_BUILD:-false}"
RUN_CLIENT="${RUN_CLIENT:-true}"
WAIT_SECONDS="${WAIT_SECONDS:-90}"

SERVER_PID=""
CONSUMER_PID=""
cleanup() {
  if [[ -n "${CLIENT_PID:-}" ]] && kill -0 "$CLIENT_PID" 2>/dev/null; then
    kill "$CLIENT_PID" 2>/dev/null || true
  fi
  if [[ -n "${CONSUMER_PID:-}" ]] && kill -0 "$CONSUMER_PID" 2>/dev/null; then
    kill "$CONSUMER_PID" 2>/dev/null || true
  fi
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

mkdir -p "$LOG_DIR"

server_log="$LOG_DIR/server-v2.log"
consumer_log="$LOG_DIR/consumer-v3.log"
client_log="$LOG_DIR/client-part2.log"

echo "ChatFlow local runner"
echo "Root: $ROOT_DIR"
echo "Logs: $LOG_DIR"
echo "Server URL for client: $SERVER_URL"
echo

build_module() {
  local module_dir="$1"
  echo "Building $module_dir..."
  (cd "$ROOT_DIR/$module_dir" && mvn -q -DskipTests package)
}

find_jar() {
  local module_dir="$1"
  local jar
  jar="$(find "$ROOT_DIR/$module_dir/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*' | sort | tail -n 1)"
  if [[ -z "$jar" ]]; then
    echo "Could not find built jar in $module_dir/target" >&2
    exit 1
  fi
  printf '%s' "$jar"
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local attempt=1
  while (( attempt <= WAIT_SECONDS )); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$label is ready: $url"
      return 0
    fi
    sleep 1
    ((attempt++))
  done
  echo "Timed out waiting for $label at $url" >&2
  return 1
}

if [[ "$SKIP_BUILD" != "true" ]]; then
  build_module "server-v2"
  build_module "consumer-v3"
  build_module "client-part2"
fi

server_jar="$(find_jar "server-v2")"
consumer_jar="$(find_jar "consumer-v3")"
client_jar="$(find_jar "client-part2")"

echo "Starting server-v2..."
( cd "$ROOT_DIR/server-v2" && java -jar "$server_jar" >"$server_log" 2>&1 ) &
SERVER_PID=$!

echo "Waiting for server health endpoints..."
wait_for_http "$SERVER_HEALTH_URL" "server-v2 health"
wait_for_http "$METRICS_HEALTH_URL" "server-v2 metrics API"

echo "Starting consumer-v3..."
( cd "$ROOT_DIR/consumer-v3" && java -jar "$consumer_jar" >"$consumer_log" 2>&1 ) &
CONSUMER_PID=$!

sleep 5
if ! kill -0 "$CONSUMER_PID" 2>/dev/null; then
  echo "consumer-v3 exited early. See $consumer_log" >&2
  tail -n 50 "$consumer_log" || true
  exit 1
fi

if [[ "$RUN_CLIENT" == "true" ]]; then
  echo "Running client-part2 benchmark..."
  ( cd "$ROOT_DIR/client-part2" && java -jar "$client_jar" "$SERVER_URL" ) | tee "$client_log"
  echo
  echo "Client output saved to: $client_log"
  echo "Use the metrics JSON block in that log for your screenshot."
else
  echo "RUN_CLIENT=false, leaving server-v2 and consumer-v3 running."
  echo "Server log: $server_log"
  echo "Consumer log: $consumer_log"
  echo "Press Ctrl+C to stop."
  wait
fi

echo "Done."
