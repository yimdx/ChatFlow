#!/usr/bin/env bash

set -euo pipefail

# Runs repeated medium-size load tests for a fixed duration to support the endurance section.
#
# Usage:
#   ./run-assignment3-endurance.sh <run-id-prefix> <consumer-config-filename> <duration-minutes> [total-messages] [main-phase-threads] [warmup-threads] [warmup-messages-per-thread]
#
# Example:
#   ./run-assignment3-endurance.sh endurance-30m consumer.batch-1000.flush-500.env 30 20000 12 4 200

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <run-id-prefix> <consumer-config-filename> <duration-minutes> [total-messages] [main-phase-threads] [warmup-threads] [warmup-messages-per-thread]" >&2
  exit 1
fi

RUN_ID_PREFIX="$1"
CONSUMER_CONFIG_FILE="$2"
DURATION_MINUTES="$3"
TOTAL_MESSAGES="${4:-20000}"
MAIN_PHASE_THREADS="${5:-12}"
WARMUP_THREADS="${6:-4}"
WARMUP_MESSAGES_PER_THREAD="${7:-200}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
END_EPOCH=$(( $(date +%s) + DURATION_MINUTES * 60 ))
ITERATION=1

while [ "$(date +%s)" -lt "$END_EPOCH" ]; do
  RUN_ID="${RUN_ID_PREFIX}-iter$(printf '%02d' "$ITERATION")"
  echo "=== Endurance iteration $ITERATION ($RUN_ID) ==="
  CLEAR_STATE_BEFORE_RUN=false \
  "$SCRIPT_DIR/run-assignment3-load-test.sh" \
    "$RUN_ID" \
    "$CONSUMER_CONFIG_FILE" \
    "$TOTAL_MESSAGES" \
    "$MAIN_PHASE_THREADS" \
    "$WARMUP_THREADS" \
    "$WARMUP_MESSAGES_PER_THREAD"
  ITERATION=$((ITERATION + 1))
done
