#!/bin/bash

set -euo pipefail

# End-to-end AWS deployment orchestrator (run on your local machine).
# It uploads artifacts/scripts and executes setup/deploy commands on each EC2 instance.

# Usage:
#   ./aws-deploy-all.sh --config ./config/aws.env

# Required env vars:
#   SSH_KEY_PATH
#   EC2_USER (ubuntu or ec2-user)
#   RABBITMQ_HOST
#   POSTGRES_HOST
#   SERVER_HOST
#   CONSUMER_HOSTS (comma-separated) or CONSUMER_HOST
#   CLIENT_HOSTS (comma-separated) or CLIENT_HOST

# Optional env vars:
#   SERVER_ID (default: server-1)
#   HEALTH_PORT (default: 8080)
#   RABBITMQ_USERNAME (default: admin)
#   RABBITMQ_PASSWORD (default: adminpassword)
#   DB_NAME (default: chatflow)
#   DB_USERNAME (default: chatflow)
#   DB_PASSWORD (default: chatflow)
#   BROADCAST_BASE_URL (default: http://SERVER_HOST:8082)
#   SERVER_URL_FOR_CLIENT (default: ws://SERVER_HOST:8081)

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

require_var() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "Missing required env var: $name" >&2
    exit 1
  fi
}

for v in SSH_KEY_PATH EC2_USER RABBITMQ_HOST POSTGRES_HOST SERVER_HOST; do
  require_var "$v"
done

CONSUMER_HOSTS_RAW=${CONSUMER_HOSTS:-${CONSUMER_HOST:-}}
CLIENT_HOSTS_RAW=${CLIENT_HOSTS:-${CLIENT_HOST:-}}

if [ -z "$CONSUMER_HOSTS_RAW" ]; then
  echo "Missing required env var: CONSUMER_HOSTS (or CONSUMER_HOST)" >&2
  exit 1
fi

if [ -z "$CLIENT_HOSTS_RAW" ]; then
  echo "Missing required env var: CLIENT_HOSTS (or CLIENT_HOST)" >&2
  exit 1
fi

IFS=',' read -r -a CONSUMER_HOSTS_ARR <<< "${CONSUMER_HOSTS_RAW// /}"
IFS=',' read -r -a CLIENT_HOSTS_ARR <<< "${CLIENT_HOSTS_RAW// /}"

if [ "${#CONSUMER_HOSTS_ARR[@]}" -eq 0 ] || [ -z "${CONSUMER_HOSTS_ARR[0]}" ]; then
  echo "CONSUMER_HOSTS is empty" >&2
  exit 1
fi

if [ "${#CLIENT_HOSTS_ARR[@]}" -eq 0 ] || [ -z "${CLIENT_HOSTS_ARR[0]}" ]; then
  echo "CLIENT_HOSTS is empty" >&2
  exit 1
fi

SERVER_ID=${SERVER_ID:-server-1}
HEALTH_PORT=${HEALTH_PORT:-8080}
RABBITMQ_USERNAME=${RABBITMQ_USERNAME:-admin}
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD:-adminpassword}
DB_NAME=${DB_NAME:-chatflow}
DB_USERNAME=${DB_USERNAME:-chatflow}
DB_PASSWORD=${DB_PASSWORD:-chatflow}
CONSUMER_THREADS=${CONSUMER_THREADS:-20}
BROADCAST_BASE_URL=${BROADCAST_BASE_URL:-http://${SERVER_HOST}:$((HEALTH_PORT + 2))}
SERVER_URL_FOR_CLIENT=${SERVER_URL_FOR_CLIENT:-ws://${SERVER_HOST}:$((HEALTH_PORT + 1))}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/deployment"

run_remote() {
  local host="$1"
  local cmd="$2"
  ssh -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "$EC2_USER@$host" "$cmd"
}

copy_to_remote() {
  local src="$1"
  local host="$2"
  local dst="$3"
  scp -o StrictHostKeyChecking=no -i "$SSH_KEY_PATH" "$src" "$EC2_USER@$host:$dst"
}

echo "Building artifacts locally..."
( cd "$DEPLOY_DIR" && ./build-all.sh )

echo "Uploading deployment scripts..."
ALL_HOSTS=("$RABBITMQ_HOST" "$POSTGRES_HOST" "$SERVER_HOST" "${CONSUMER_HOSTS_ARR[@]}" "${CLIENT_HOSTS_ARR[@]}")
UNIQUE_HOSTS=($(printf "%s\n" "${ALL_HOSTS[@]}" | awk '!seen[$0]++'))

for host in "${UNIQUE_HOSTS[@]}"; do
  run_remote "$host" "mkdir -p ~/chatflow-deploy"
  copy_to_remote "$DEPLOY_DIR/setup-rabbitmq.sh" "$host" "~/chatflow-deploy/"
  copy_to_remote "$DEPLOY_DIR/setup-postgres.sh" "$host" "~/chatflow-deploy/"
  copy_to_remote "$DEPLOY_DIR/deploy-server.sh" "$host" "~/chatflow-deploy/"
  copy_to_remote "$DEPLOY_DIR/deploy-consumer-v3.sh" "$host" "~/chatflow-deploy/"
  copy_to_remote "$DEPLOY_DIR/deploy-client-part2.sh" "$host" "~/chatflow-deploy/"
done

echo "Uploading jars..."
copy_to_remote "$ROOT_DIR/server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar" "$SERVER_HOST" "~/chatflow-deploy/"
for host in "${CONSUMER_HOSTS_ARR[@]}"; do
  copy_to_remote "$ROOT_DIR/consumer-v3/target/MessageConsumerV3-1.0-SNAPSHOT.jar" "$host" "~/chatflow-deploy/"
done
CLIENT_JAR=$(find "$ROOT_DIR/client-part2/target" -maxdepth 1 -type f -name "*.jar" ! -name "original-*" | sort | tail -n 1)
for host in "${CLIENT_HOSTS_ARR[@]}"; do
  copy_to_remote "$CLIENT_JAR" "$host" "~/chatflow-deploy/client-part2-runner.jar"
done

echo "Setting executable permissions..."
for host in "${UNIQUE_HOSTS[@]}"; do
  run_remote "$host" "chmod +x ~/chatflow-deploy/*.sh"
done

echo "Installing RabbitMQ host dependencies and starting RabbitMQ..."
run_remote "$RABBITMQ_HOST" "cd ~/chatflow-deploy && ./setup-rabbitmq.sh"

echo "Installing PostgreSQL host dependencies and creating DB..."
run_remote "$POSTGRES_HOST" "cd ~/chatflow-deploy && DB_NAME=$DB_NAME DB_USER=$DB_USERNAME DB_PASSWORD=$DB_PASSWORD ./setup-postgres.sh"

echo "Starting server-v2..."
run_remote "$SERVER_HOST" "cd ~/chatflow-deploy && RABBITMQ_USERNAME=$RABBITMQ_USERNAME RABBITMQ_PASSWORD=$RABBITMQ_PASSWORD METRICS_DB_NAME=$DB_NAME METRICS_DB_USERNAME=$DB_USERNAME METRICS_DB_PASSWORD=$DB_PASSWORD ./deploy-server.sh $RABBITMQ_HOST $SERVER_ID $POSTGRES_HOST $HEALTH_PORT"

echo "Starting consumer-v3..."
for host in "${CONSUMER_HOSTS_ARR[@]}"; do
  run_remote "$host" "cd ~/chatflow-deploy && RABBITMQ_USERNAME=$RABBITMQ_USERNAME RABBITMQ_PASSWORD=$RABBITMQ_PASSWORD DB_NAME=$DB_NAME DB_USERNAME=$DB_USERNAME DB_PASSWORD=$DB_PASSWORD ./deploy-consumer-v3.sh $RABBITMQ_HOST $POSTGRES_HOST $BROADCAST_BASE_URL $CONSUMER_THREADS"
done

echo "Ready. To run the client benchmark on each client host:"
for host in "${CLIENT_HOSTS_ARR[@]}"; do
  echo "ssh -i $SSH_KEY_PATH $EC2_USER@$host 'cd ~/chatflow-deploy && METRICS_API_URL=http://$SERVER_HOST:$((HEALTH_PORT + 3)) ./deploy-client-part2.sh $SERVER_URL_FOR_CLIENT'"
done

echo "Deployment complete."
