#!/bin/bash

set -euo pipefail

# Deploy and run client-part2
# Usage:
#   ./deploy-client-part2.sh --config ./config/client.env
#   ./deploy-client-part2.sh <server-websocket-url>

CONFIG_FILE=""

if [ "${1:-}" = "--config" ] || [ "${1:-}" = "-c" ]; then
  if [ "$#" -lt 2 ]; then
    echo "Usage: $0 --config <config-file>"
    exit 1
  fi
  CONFIG_FILE=$2
  shift 2
fi

if [ -n "$CONFIG_FILE" ]; then
  if [ ! -f "$CONFIG_FILE" ]; then
    echo "Config file not found: $CONFIG_FILE"
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$CONFIG_FILE"
  set +a
fi

if [ "$#" -lt 1 ] && [ -z "${SERVER_URL:-}" ]; then
  echo "Usage: $0 <server-websocket-url>"
  echo "Or:    $0 --config ./config/client.env"
  exit 1
fi

SERVER_URL=${SERVER_URL:-${1:-}}
METRICS_API_URL=${METRICS_API_URL:-}

if [ -f "./client-part2-runner.jar" ]; then
  CLIENT_JAR="./client-part2-runner.jar"
else
  CLIENT_JAR=$(find ../client-part2/target -maxdepth 1 -type f -name "*.jar" ! -name "original-*" | sort | tail -n 1)
  if [ -z "$CLIENT_JAR" ]; then
    echo "Error: client-part2 jar not found."
    echo "Expected one of:"
    echo "  ./client-part2-runner.jar"
    echo "  ../client-part2/target/*.jar"
    exit 1
  fi
  cp "$CLIENT_JAR" ./client-part2-runner.jar
fi

echo "Running client-part2 against: $SERVER_URL"
if [ -n "$METRICS_API_URL" ]; then
  echo "Using METRICS_API_URL override: $METRICS_API_URL"
fi

if [ -n "$METRICS_API_URL" ]; then
  METRICS_API_URL="$METRICS_API_URL" java -jar client-part2-runner.jar "$SERVER_URL" | tee client-part2.log
else
  java -jar client-part2-runner.jar "$SERVER_URL" | tee client-part2.log
fi

echo "Client run complete. Log: client-part2.log"
