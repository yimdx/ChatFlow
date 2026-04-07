#!/bin/bash

# PostgreSQL metrics collector for Assignment 3.
# Requires psql and access to target database.

set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-chatflow}"
PGUSER="${PGUSER:-chatflow}"
INTERVAL="${INTERVAL:-5}"
OUTPUT_FILE="${OUTPUT_FILE:-postgres_metrics.csv}"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql not found. Install PostgreSQL client first."
  exit 1
fi

echo "timestamp,connections,cache_hit_ratio,xact_commit_per_s,blks_read_per_s,blks_hit_per_s" > "$OUTPUT_FILE"

echo "Postgres metrics monitor started: $OUTPUT_FILE"

while true; do
  TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  CONNECTIONS=$(psql -qtAX -c "SELECT count(*) FROM pg_stat_activity WHERE datname='${PGDATABASE}';")
  CACHE_HIT_RATIO=$(psql -qtAX -c "SELECT COALESCE(round(100.0*sum(blks_hit)/NULLIF(sum(blks_hit+blks_read),0),2),0) FROM pg_stat_database WHERE datname='${PGDATABASE}';")

  STATS=$(psql -qtAX -F',' -c "SELECT xact_commit,blks_read,blks_hit FROM pg_stat_database WHERE datname='${PGDATABASE}';")

  if [[ -z "${PREV_STATS:-}" ]]; then
    COMMIT_PS=0
    READ_PS=0
    HIT_PS=0
  else
    IFS=',' read -r CUR_COMMIT CUR_READ CUR_HIT <<< "$STATS"
    IFS=',' read -r PREV_COMMIT PREV_READ PREV_HIT <<< "$PREV_STATS"
    COMMIT_PS=$(( (CUR_COMMIT - PREV_COMMIT) / INTERVAL ))
    READ_PS=$(( (CUR_READ - PREV_READ) / INTERVAL ))
    HIT_PS=$(( (CUR_HIT - PREV_HIT) / INTERVAL ))
  fi

  PREV_STATS="$STATS"

  echo "$TS,$CONNECTIONS,$CACHE_HIT_RATIO,$COMMIT_PS,$READ_PS,$HIT_PS" >> "$OUTPUT_FILE"
  sleep "$INTERVAL"
done
