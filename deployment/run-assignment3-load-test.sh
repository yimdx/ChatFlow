#!/usr/bin/env bash

set -euo pipefail

# Runs one Assignment 3 load test (baseline/stress/custom) end-to-end.
#
# Usage:
#   ./run-assignment3-load-test.sh <run-id> <consumer-config-filename> <total-messages> [main-phase-threads] [warmup-threads] [warmup-messages-per-thread]
#
# Example:
#   ./run-assignment3-load-test.sh baseline-500k consumer.batch-1000.flush-500.env 500000 16 8 500

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <run-id> <consumer-config-filename> <total-messages> [main-phase-threads] [warmup-threads] [warmup-messages-per-thread]" >&2
  exit 1
fi

RUN_ID="$1"
CONSUMER_CONFIG_FILE="$2"
TOTAL_MESSAGES="$3"
MAIN_PHASE_THREADS="${4:-16}"
WARMUP_THREADS="${5:-8}"
WARMUP_MESSAGES_PER_THREAD="${6:-500}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

KEY_PATH="${KEY_PATH:-/Users/xuefengli/26spring/distribution/chatflow.pem}"
RESULTS_ROOT="${RESULTS_ROOT:-/Users/xuefengli/26spring/distribution/ChatFlow/load-tests/results}"

MQ_HOST="${MQ_HOST:-35.93.226.130}"
POSTGRES_HOST="${POSTGRES_HOST:-35.92.95.79}"
CONSUMER_HOST="${CONSUMER_HOST:-35.95.64.73}"
CLIENT_HOST="${CLIENT_HOST:-35.95.70.55}"
SERVER_HOST="${SERVER_HOST:-16.145.87.63}"
EC2_USER="${EC2_USER:-ec2-user}"

CLEAR_STATE_BEFORE_RUN="${CLEAR_STATE_BEFORE_RUN:-true}"

export KEY_PATH RESULTS_ROOT MQ_HOST POSTGRES_HOST CONSUMER_HOST CLIENT_HOST SERVER_HOST EC2_USER
export CLIENT_TOTAL_MESSAGES="$TOTAL_MESSAGES"
export CLIENT_MAIN_PHASE_THREADS="$MAIN_PHASE_THREADS"
export CLIENT_WARMUP_THREADS="$WARMUP_THREADS"
export CLIENT_WARMUP_MESSAGES_PER_THREAD="$WARMUP_MESSAGES_PER_THREAD"
export CLEAR_STATE_BEFORE_RUN

"$SCRIPT_DIR/run-assignment3-batch-test.sh" "$RUN_ID" "$CONSUMER_CONFIG_FILE"
