#!/bin/bash

# Assignment 3 load-test helper.
# Runs API snapshots after test execution and stores outputs in load-tests/results.

set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8083}"
OUTPUT_DIR="${OUTPUT_DIR:-$(dirname "$0")/results}"
START="${START:-$(date -u -v-1H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)}"
END="${END:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"

mkdir -p "$OUTPUT_DIR"
TS="$(date -u +%Y%m%d_%H%M%S)"

curl -s "$API_BASE/metrics" > "$OUTPUT_DIR/metrics_${TS}.json"
curl -s "$API_BASE/api/v1/analytics/active-users?start=$START&end=$END" > "$OUTPUT_DIR/active_users_${TS}.json"
curl -s "$API_BASE/api/v1/analytics/top-users?start=$START&end=$END&n=10" > "$OUTPUT_DIR/top_users_${TS}.json"
curl -s "$API_BASE/api/v1/analytics/top-rooms?start=$START&end=$END&n=10" > "$OUTPUT_DIR/top_rooms_${TS}.json"
curl -s "$API_BASE/api/v1/analytics/messages-rate?start=$START&end=$END&granularity=minute" > "$OUTPUT_DIR/message_rate_${TS}.json"

echo "Saved API snapshots to $OUTPUT_DIR"
