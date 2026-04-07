#!/bin/bash

set -euo pipefail

# Deploy consumer-v3 script
# Usage:
#   ./deploy-consumer-v3.sh --config ./config/consumer.env
#   ./deploy-consumer-v3.sh <rabbitmq-host> <postgres-host> <broadcast-base-url> [threads]

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

if [ "$#" -lt 3 ] && [ -z "${RABBITMQ_HOST:-}" -o -z "${POSTGRES_HOST:-}" -o -z "${BROADCAST_BASE_URL:-}" ]; then
  echo "Usage: $0 <rabbitmq-host> <postgres-host> <broadcast-base-url> [threads]"
  echo "Example: $0 10.0.1.100 10.0.2.10 http://10.0.3.10:8082 20"
  echo "Or:    $0 --config ./config/consumer.env"
  exit 1
fi

RABBITMQ_HOST=${RABBITMQ_HOST:-${1:-}}
POSTGRES_HOST=${POSTGRES_HOST:-${2:-}}
BROADCAST_BASE_URL=${BROADCAST_BASE_URL:-${3:-}}
THREADS=${CONSUMER_THREAD_COUNT:-${4:-20}}

if [ -z "$RABBITMQ_HOST" ] || [ -z "$POSTGRES_HOST" ] || [ -z "$BROADCAST_BASE_URL" ]; then
  echo "Missing required values. Need RABBITMQ_HOST, POSTGRES_HOST, BROADCAST_BASE_URL"
  exit 1
fi

RABBITMQ_PORT=${RABBITMQ_PORT:-5672}
RABBITMQ_USERNAME=${RABBITMQ_USERNAME:-admin}
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD:-adminpassword}

DB_NAME=${DB_NAME:-chatflow}
DB_USERNAME=${DB_USERNAME:-chatflow}
DB_PASSWORD=${DB_PASSWORD:-chatflow}
DB_PORT=${DB_PORT:-5432}
DB_POOL_MAX_SIZE=${DB_POOL_MAX_SIZE:-30}

DB_BATCH_SIZE=${DB_BATCH_SIZE:-1000}
DB_FLUSH_INTERVAL_MS=${DB_FLUSH_INTERVAL_MS:-500}
DB_WRITER_THREAD_COUNT=${DB_WRITER_THREAD_COUNT:-4}
DB_RETRY_MAX=${DB_RETRY_MAX:-3}
DB_RETRY_BASE_MS=${DB_RETRY_BASE_MS:-200}

BROADCAST_QUEUE_CAPACITY=${BROADCAST_QUEUE_CAPACITY:-200000}
BROADCAST_BATCH_SIZE=${BROADCAST_BATCH_SIZE:-50}
BROADCAST_FLUSH_INTERVAL_MS=${BROADCAST_FLUSH_INTERVAL_MS:-25}

echo "Deploying consumer-v3..."
echo "RabbitMQ Host: $RABBITMQ_HOST"
echo "PostgreSQL Host: $POSTGRES_HOST"
echo "Broadcast Base URL: $BROADCAST_BASE_URL"
echo "Consumer Threads: $THREADS"

if [ -f "../consumer-v3/target/MessageConsumerV3-1.0-SNAPSHOT.jar" ]; then
  JAR_PATH="../consumer-v3/target/MessageConsumerV3-1.0-SNAPSHOT.jar"
elif [ -f "./MessageConsumerV3-1.0-SNAPSHOT.jar" ]; then
  JAR_PATH="./MessageConsumerV3-1.0-SNAPSHOT.jar"
else
  echo "Error: consumer-v3 JAR not found."
  echo "Expected one of:"
  echo "  ../consumer-v3/target/MessageConsumerV3-1.0-SNAPSHOT.jar"
  echo "  ./MessageConsumerV3-1.0-SNAPSHOT.jar"
  exit 1
fi

TARGET_JAR="./MessageConsumerV3-1.0-SNAPSHOT.jar"
if [ "$(realpath "$JAR_PATH")" != "$(realpath "$TARGET_JAR" 2>/dev/null || echo "$TARGET_JAR")" ]; then
  cp "$JAR_PATH" "$TARGET_JAR"
fi

nohup env \
  RABBITMQ_HOST=$RABBITMQ_HOST \
  RABBITMQ_PORT=$RABBITMQ_PORT \
  RABBITMQ_USERNAME=$RABBITMQ_USERNAME \
  RABBITMQ_PASSWORD=$RABBITMQ_PASSWORD \
  CONSUMER_THREAD_COUNT=$THREADS \
  ROOM_COUNT=20 \
  DB_URL="jdbc:postgresql://${POSTGRES_HOST}:${DB_PORT}/${DB_NAME}" \
  DB_USERNAME=$DB_USERNAME \
  DB_PASSWORD=$DB_PASSWORD \
  DB_POOL_MAX_SIZE=$DB_POOL_MAX_SIZE \
  DB_BATCH_SIZE=$DB_BATCH_SIZE \
  DB_FLUSH_INTERVAL_MS=$DB_FLUSH_INTERVAL_MS \
  DB_WRITER_THREAD_COUNT=$DB_WRITER_THREAD_COUNT \
  DB_RETRY_MAX=$DB_RETRY_MAX \
  DB_RETRY_BASE_MS=$DB_RETRY_BASE_MS \
  BROADCAST_SERVER_BASE_URL=$BROADCAST_BASE_URL \
  BROADCAST_QUEUE_CAPACITY=$BROADCAST_QUEUE_CAPACITY \
  BROADCAST_BATCH_SIZE=$BROADCAST_BATCH_SIZE \
  BROADCAST_FLUSH_INTERVAL_MS=$BROADCAST_FLUSH_INTERVAL_MS \
  java -jar "$TARGET_JAR" \
  > consumer-v3.log 2>&1 &

PID=$!
echo "consumer-v3 started with PID: $PID"
echo "Log file: consumer-v3.log"
