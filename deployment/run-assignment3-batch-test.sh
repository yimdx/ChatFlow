#!/usr/bin/env bash

set -euo pipefail

# Runs one Assignment 3 batch/flush experiment end-to-end from the local machine.
#
# Usage:
#   ./run-assignment3-batch-test.sh <run-id> <consumer-config-filename>
#
# Example:
#   ./run-assignment3-batch-test.sh run-b1-batch100-flush100 consumer.batch-100.flush-100.env

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <run-id> <consumer-config-filename>" >&2
  exit 1
fi

RUN_ID="$1"
CONSUMER_CONFIG_FILE="$2"

KEY_PATH="${KEY_PATH:-/Users/xuefengli/26spring/distribution/chatflow.pem}"
RESULTS_ROOT="${RESULTS_ROOT:-/Users/xuefengli/26spring/distribution/ChatFlow/load-tests/results}"

MQ_HOST="${MQ_HOST:-35.93.226.130}"
POSTGRES_HOST="${POSTGRES_HOST:-35.92.95.79}"
CONSUMER_HOST="${CONSUMER_HOST:-35.95.64.73}"
CLIENT_HOST="${CLIENT_HOST:-35.95.70.55}"
SERVER_HOST="${SERVER_HOST:-16.145.87.63}"
EC2_USER="${EC2_USER:-ec2-user}"

CLIENT_MAIN_PHASE_THREADS="${CLIENT_MAIN_PHASE_THREADS:-16}"
CLIENT_WARMUP_THREADS="${CLIENT_WARMUP_THREADS:-8}"
CLIENT_WARMUP_MESSAGES_PER_THREAD="${CLIENT_WARMUP_MESSAGES_PER_THREAD:-200}"
CLIENT_TOTAL_MESSAGES="${CLIENT_TOTAL_MESSAGES:-20000}"

CLEAR_STATE_BEFORE_RUN="${CLEAR_STATE_BEFORE_RUN:-true}"

REMOTE_RESULTS_DIR="~/chatflow-results/$RUN_ID"
LOCAL_RESULTS_DIR="$RESULTS_ROOT/$RUN_ID"

ssh_cmd() {
  local host="$1"
  local command="$2"
  ssh -i "$KEY_PATH" "$EC2_USER@$host" "$command"
}

scp_from() {
  local host="$1"
  local remote_path="$2"
  local local_path="$3"
  scp -i "$KEY_PATH" "$EC2_USER@$host:$remote_path" "$local_path"
}

echo "=== Assignment 3 Batch Test ==="
echo "Run ID: $RUN_ID"
echo "Consumer config: $CONSUMER_CONFIG_FILE"
echo "Client load: TOTAL_MESSAGES=$CLIENT_TOTAL_MESSAGES, MAIN_PHASE_THREADS=$CLIENT_MAIN_PHASE_THREADS, WARMUP_THREADS=$CLIENT_WARMUP_THREADS, WARMUP_MESSAGES_PER_THREAD=$CLIENT_WARMUP_MESSAGES_PER_THREAD"
echo

mkdir -p "$LOCAL_RESULTS_DIR"

echo "[1/7] Preparing remote result directories..."
for host in "$MQ_HOST" "$POSTGRES_HOST" "$CONSUMER_HOST" "$CLIENT_HOST"; do
  ssh_cmd "$host" "mkdir -p $REMOTE_RESULTS_DIR"
done

if [ "$CLEAR_STATE_BEFORE_RUN" = "true" ]; then
  echo "[2/7] Clearing RabbitMQ queues and PostgreSQL tables..."
  ssh_cmd "$MQ_HOST" "for q in \$(sudo rabbitmqctl list_queues name | awk '/^room\\./ {print \$1}'); do sudo rabbitmqctl purge_queue \"\$q\"; done; sudo rabbitmqctl purge_queue chat.persist.dlq || true"
  ssh_cmd "$POSTGRES_HOST" "sudo -u postgres psql -d chatflow -c \"TRUNCATE TABLE message_minute_stats, user_room_activity, chat_messages;\""
else
  echo "[2/7] Skipping queue/database reset"
fi

echo "[3/7] Restarting consumer with $CONSUMER_CONFIG_FILE ..."
if ! ssh_cmd "$CONSUMER_HOST" "cd ~/chatflow-deploy && pkill -f MessageConsumerV3 || true && sleep 2 && ./deploy-consumer-v3.sh --config ./config/$CONSUMER_CONFIG_FILE && for i in 1 2 3 4 5; do pgrep -f 'MessageConsumerV3-1.0-SNAPSHOT.jar' >/dev/null && exit 0; sleep 2; done; exit 1"; then
  echo "Consumer failed to restart with config: $CONSUMER_CONFIG_FILE" >&2
  echo "Check on consumer host:" >&2
  echo "  cd ~/chatflow-deploy" >&2
  echo "  ./deploy-consumer-v3.sh --config ./config/$CONSUMER_CONFIG_FILE" >&2
  echo "  tail -n 100 ~/chatflow-deploy/consumer-v3.log" >&2
  exit 1
fi

echo "[4/7] Running client benchmark..."
ssh_cmd "$CLIENT_HOST" "mkdir -p $REMOTE_RESULTS_DIR && cd ~/chatflow-deploy && MAIN_PHASE_THREADS=$CLIENT_MAIN_PHASE_THREADS WARMUP_THREADS=$CLIENT_WARMUP_THREADS WARMUP_MESSAGES_PER_THREAD=$CLIENT_WARMUP_MESSAGES_PER_THREAD TOTAL_MESSAGES=$CLIENT_TOTAL_MESSAGES ./deploy-client-part2.sh --config ./config/client.env | tee $REMOTE_RESULTS_DIR/client-output.txt && cp ~/chatflow-deploy/client-part2.log $REMOTE_RESULTS_DIR/ && grep -n 'SERVER-V2 METRICS API RESPONSE' -A 200 ~/chatflow-deploy/client-part2.log > $REMOTE_RESULTS_DIR/metrics-api.txt"

echo "[5/7] Capturing consumer, RabbitMQ, server, and PostgreSQL summaries..."
ssh_cmd "$CONSUMER_HOST" "tail -n 200 ~/chatflow-deploy/consumer-v3.log > $REMOTE_RESULTS_DIR/consumer-tail.txt"
ssh_cmd "$MQ_HOST" "sudo rabbitmqctl list_queues name messages consumers > $REMOTE_RESULTS_DIR/rabbitmq-queues.txt"
ssh_cmd "$SERVER_HOST" "curl -s http://localhost:8083/metrics > $REMOTE_RESULTS_DIR/server-metrics.json"
ssh_cmd "$POSTGRES_HOST" "{ echo 'chat_messages:'; sudo -u postgres psql -d chatflow -c \"select count(*) from chat_messages;\"; echo; echo 'user_room_activity:'; sudo -u postgres psql -d chatflow -c \"select count(*) from user_room_activity;\"; echo; echo 'message_minute_stats:'; sudo -u postgres psql -d chatflow -c \"select count(*) from message_minute_stats;\"; echo; echo 'latest message timestamp:'; sudo -u postgres psql -d chatflow -c \"select max(message_ts) from chat_messages;\"; } > $REMOTE_RESULTS_DIR/postgres-summary.txt"

echo "[6/7] Copying results back to $LOCAL_RESULTS_DIR ..."
scp_from "$CLIENT_HOST" "$REMOTE_RESULTS_DIR/client-output.txt" "$LOCAL_RESULTS_DIR/"
scp_from "$CLIENT_HOST" "$REMOTE_RESULTS_DIR/client-part2.log" "$LOCAL_RESULTS_DIR/"
scp_from "$CLIENT_HOST" "$REMOTE_RESULTS_DIR/metrics-api.txt" "$LOCAL_RESULTS_DIR/"
scp_from "$CONSUMER_HOST" "$REMOTE_RESULTS_DIR/consumer-tail.txt" "$LOCAL_RESULTS_DIR/"
scp_from "$MQ_HOST" "$REMOTE_RESULTS_DIR/rabbitmq-queues.txt" "$LOCAL_RESULTS_DIR/"
scp_from "$SERVER_HOST" "$REMOTE_RESULTS_DIR/server-metrics.json" "$LOCAL_RESULTS_DIR/"
scp_from "$POSTGRES_HOST" "$REMOTE_RESULTS_DIR/postgres-summary.txt" "$LOCAL_RESULTS_DIR/"

echo "[7/7] Done."
echo "Saved artifacts:"
echo "  $LOCAL_RESULTS_DIR/client-output.txt"
echo "  $LOCAL_RESULTS_DIR/client-part2.log"
echo "  $LOCAL_RESULTS_DIR/metrics-api.txt"
echo "  $LOCAL_RESULTS_DIR/consumer-tail.txt"
echo "  $LOCAL_RESULTS_DIR/rabbitmq-queues.txt"
echo "  $LOCAL_RESULTS_DIR/server-metrics.json"
echo "  $LOCAL_RESULTS_DIR/postgres-summary.txt"
