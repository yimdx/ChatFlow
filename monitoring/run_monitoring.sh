#!/bin/bash
# Complete monitoring setup script
# Pure bash - no Python required
# Requires: curl, jq (for RabbitMQ monitoring)

set -e

# Configuration
RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}"
RABBITMQ_PORT="${RABBITMQ_PORT:-15672}"
RABBITMQ_USER="${RABBITMQ_USER:-admin}"
RABBITMQ_PASS="${RABBITMQ_PASS:-adminpassword}"

INTERVAL="${INTERVAL:-5}"
OUTPUT_DIR="${OUTPUT_DIR:-monitoring_results}"
MONITOR_SYSTEM="${MONITOR_SYSTEM:-false}"  # Set to "true" if running on server

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================"
echo "ChatFlow Monitoring Setup"
echo "========================================"

# Create output directory
mkdir -p "$OUTPUT_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Check dependencies
echo -e "\n${YELLOW}Checking dependencies...${NC}"
command -v curl >/dev/null 2>&1 || { echo -e "${RED}Error: curl is required${NC}"; exit 1; }
command -v jq >/dev/null 2>&1 || { echo -e "${RED}Error: jq is required${NC}"; echo "Install: brew install jq"; exit 1; }

echo -e "${GREEN}✓ Dependencies OK${NC}"

# Check RabbitMQ connectivity
echo -e "\n${YELLOW}Checking RabbitMQ connectivity...${NC}"
if curl -s -u "$RABBITMQ_USER:$RABBITMQ_PASS" \
   "http://$RABBITMQ_HOST:$RABBITMQ_PORT/api/overview" > /dev/null; then
    echo -e "${GREEN}✓ RabbitMQ Management API accessible${NC}"
else
    echo -e "${RED}✗ Cannot connect to RabbitMQ Management API${NC}"
    echo "  URL: http://$RABBITMQ_HOST:$RABBITMQ_PORT"
    echo "  Make sure RabbitMQ management plugin is enabled:"
    echo "    docker exec rabbitmq rabbitmq-plugins enable rabbitmq_management"
    exit 1
fi

# Start monitoring
echo -e "\n${GREEN}Starting monitoring...${NC}"
echo "Output directory: $OUTPUT_DIR"
echo "Interval: ${INTERVAL}s"
echo ""

QUEUE_OUTPUT="$OUTPUT_DIR/queue_metrics_${TIMESTAMP}.csv"
SYSTEM_OUTPUT="$OUTPUT_DIR/system_metrics_${TIMESTAMP}.csv"

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Start RabbitMQ monitoring in background
echo "Starting RabbitMQ monitor..."
RABBITMQ_HOST="$RABBITMQ_HOST" \
RABBITMQ_PORT="$RABBITMQ_PORT" \
RABBITMQ_USER="$RABBITMQ_USER" \
RABBITMQ_PASS="$RABBITMQ_PASS" \
INTERVAL="$INTERVAL" \
OUTPUT_FILE="$QUEUE_OUTPUT" \
"$SCRIPT_DIR/monitor_rabbitmq.sh" &
RABBITMQ_PID=$!

# Start system monitoring if enabled
SYSTEM_PID=""
if [ "$MONITOR_SYSTEM" = "true" ]; then
    echo "Starting system metrics monitor..."
    INTERVAL="$INTERVAL" \
    OUTPUT_FILE="$SYSTEM_OUTPUT" \
    "$SCRIPT_DIR/monitor_servers.sh" &
    SYSTEM_PID=$!
else
    echo "Note: Run 'monitor_servers.sh' ON each server instance for system metrics"
fi

echo -e "\n${GREEN}Monitoring active!${NC}"
echo "Press Ctrl+C to stop and generate report"
echo ""
echo "RabbitMQ monitor PID: $RABBITMQ_PID"
[ -n "$SYSTEM_PID" ] && echo "System monitor PID: $SYSTEM_PID"
echo ""

# Handle cleanup
cleanup() {
    echo -e "\n\n${YELLOW}Stopping monitors...${NC}"
    kill $RABBITMQ_PID 2>/dev/null || true
    [ -n "$SYSTEM_PID" ] && kill $SYSTEM_PID 2>/dev/null || true
    
    # Wait a moment for processes to clean up
    sleep 1
    
    echo -e "\n${GREEN}Generating analysis...${NC}"
    
    if [ -f "$QUEUE_OUTPUT" ]; then
        "$SCRIPT_DIR/analyze_metrics.sh" "$QUEUE_OUTPUT"
        
        echo -e "\n${GREEN}✓ Analysis complete!${NC}"
        echo "Results saved to: $OUTPUT_DIR/"
        echo "  - Queue metrics: $QUEUE_OUTPUT"
        [ -n "$SYSTEM_PID" ] && [ -f "$SYSTEM_OUTPUT" ] && echo "  - System metrics: $SYSTEM_OUTPUT"
    else
        echo "No metrics file found"
    fi
    
    exit 0
}

trap cleanup INT TERM

# Wait for monitoring processes
wait
