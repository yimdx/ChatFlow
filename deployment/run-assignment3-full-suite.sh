#!/usr/bin/env bash

set -euo pipefail

# Convenience wrapper for the full Assignment 3 experiment suite.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Running batch tuning matrix ==="
"$SCRIPT_DIR/run-assignment3-batch-matrix.sh"

echo "=== Running baseline 500k ==="
"$SCRIPT_DIR/run-assignment3-load-test.sh" baseline-500k consumer.batch-1000.flush-500.env 500000 16 8 500

echo "=== Running stress 1000k ==="
"$SCRIPT_DIR/run-assignment3-load-test.sh" stress-1000k consumer.batch-1000.flush-500.env 1000000 16 8 500

echo "=== Running endurance 30m ==="
"$SCRIPT_DIR/run-assignment3-endurance.sh" endurance-30m consumer.batch-1000.flush-500.env 30 20000 12 4 200
