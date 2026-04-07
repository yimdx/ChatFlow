#!/usr/bin/env bash

set -euo pipefail

# Runs the 5 Assignment 3 batch/flush tuning experiments in sequence.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_DIR/run-assignment3-batch-test.sh" run-b1-batch100-flush100 consumer.batch-100.flush-100.env
"$SCRIPT_DIR/run-assignment3-batch-test.sh" run-b2-batch500-flush100 consumer.batch-500.flush-100.env
"$SCRIPT_DIR/run-assignment3-batch-test.sh" run-b3-batch1000-flush500 consumer.batch-1000.flush-500.env
"$SCRIPT_DIR/run-assignment3-batch-test.sh" run-b4-batch5000-flush500 consumer.batch-5000.flush-500.env
"$SCRIPT_DIR/run-assignment3-batch-test.sh" run-b5-batch5000-flush1000 consumer.batch-5000.flush-1000.env
