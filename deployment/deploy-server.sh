#!/bin/bash

set -euo pipefail

# Deploy Server-v2 Script
# Usage:
#   ./deploy-server.sh --config ./config/server.env
#   ./deploy-server.sh <rabbitmq-host> <server-id> <postgres-host> [health-port]

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

if [ "$#" -lt 3 ] && [ -z "${RABBITMQ_HOST:-}" -o -z "${SERVER_ID:-}" -o -z "${POSTGRES_HOST:-}" ]; then
    echo "Usage: $0 <rabbitmq-host> <server-id> <postgres-host> [health-port]"
    echo "Example: $0 10.0.1.100 server-1 10.0.2.10 8080"
    echo "  -> Health on :8080, WebSocket on :8081, Broadcast on :8082, Metrics on :8083"
    echo "Or:    $0 --config ./config/server.env"
    exit 1
fi

RABBITMQ_HOST=${RABBITMQ_HOST:-${1:-}}
SERVER_ID=${SERVER_ID:-${2:-}}
POSTGRES_HOST=${POSTGRES_HOST:-${3:-}}
PORT=${HEALTH_PORT:-${4:-8080}}

if [ -z "$RABBITMQ_HOST" ] || [ -z "$SERVER_ID" ] || [ -z "$POSTGRES_HOST" ]; then
    echo "Missing required values. Need RABBITMQ_HOST, SERVER_ID, POSTGRES_HOST"
    exit 1
fi

RABBITMQ_PORT=${RABBITMQ_PORT:-5672}
RABBITMQ_USERNAME=${RABBITMQ_USERNAME:-admin}
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD:-adminpassword}

METRICS_DB_NAME=${METRICS_DB_NAME:-chatflow}
METRICS_DB_USERNAME=${METRICS_DB_USERNAME:-chatflow}
METRICS_DB_PASSWORD=${METRICS_DB_PASSWORD:-chatflow}
METRICS_DB_PORT=${METRICS_DB_PORT:-5432}
METRICS_DB_POOL_SIZE=${METRICS_DB_POOL_SIZE:-10}

echo "Deploying server-v2..."
echo "RabbitMQ Host: $RABBITMQ_HOST"
echo "Server ID: $SERVER_ID"
echo "PostgreSQL Host: $POSTGRES_HOST"
echo "Port: $PORT"

# Resolve JAR path (repo layout or standalone deployment folder)
if [ -f "../server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar" ]; then
    JAR_PATH="../server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar"
elif [ -f "./WebSocketServer-1.0-SNAPSHOT.jar" ]; then
    JAR_PATH="./WebSocketServer-1.0-SNAPSHOT.jar"
else
    echo "Error: server-v2 JAR not found."
    echo "Expected one of:"
    echo "  ../server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar"
    echo "  ./WebSocketServer-1.0-SNAPSHOT.jar"
    exit 1
fi

TARGET_JAR="./WebSocketServer-1.0-SNAPSHOT.jar"
if [ "$(realpath "$JAR_PATH")" != "$(realpath "$TARGET_JAR" 2>/dev/null || echo "$TARGET_JAR")" ]; then
    cp "$JAR_PATH" "$TARGET_JAR"
fi

# Run server (server-v2 reads configuration from environment variables, not --args)
nohup env \
    HEALTH_PORT=$PORT \
    WEBSOCKET_PORT=$((PORT + 1)) \
    BROADCAST_PORT=$((PORT + 2)) \
    METRICS_PORT=$((PORT + 3)) \
    RABBITMQ_HOST=$RABBITMQ_HOST \
    RABBITMQ_PORT=$RABBITMQ_PORT \
    RABBITMQ_USERNAME=$RABBITMQ_USERNAME \
    RABBITMQ_PASSWORD=$RABBITMQ_PASSWORD \
    SERVER_ID=$SERVER_ID \
    RABBITMQ_POOL_SIZE=20 \
    ROOM_COUNT=20 \
    METRICS_DB_URL="jdbc:postgresql://$POSTGRES_HOST:$METRICS_DB_PORT/$METRICS_DB_NAME" \
    METRICS_DB_USERNAME=$METRICS_DB_USERNAME \
    METRICS_DB_PASSWORD=$METRICS_DB_PASSWORD \
    METRICS_DB_POOL_SIZE=$METRICS_DB_POOL_SIZE \
    java -jar "$TARGET_JAR" \
    > server-$SERVER_ID.log 2>&1 &

PID=$!
echo "Server started with PID: $PID"
echo "Log file: server-$SERVER_ID.log"
echo "Health check:    curl http://localhost:$PORT/health"
echo "WebSocket port:  $((PORT + 1))"
echo "Broadcast port:  $((PORT + 2))"
echo "Metrics port:    $((PORT + 3))"
