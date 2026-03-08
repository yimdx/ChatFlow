#!/bin/bash
# RabbitMQ Queue Monitoring Script
# Pure bash - no Python required
# Requires: curl, jq

set -euo pipefail

# Default configuration
RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}"
RABBITMQ_PORT="${RABBITMQ_PORT:-15672}"
RABBITMQ_USER="${RABBITMQ_USER:-admin}"
RABBITMQ_PASS="${RABBITMQ_PASS:-adminpassword}"
INTERVAL="${INTERVAL:-5}"
OUTPUT_FILE="${OUTPUT_FILE:-queue_metrics.csv}"

# Colors
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

# Check dependencies
command -v curl >/dev/null 2>&1 || { echo "Error: curl is required"; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "Error: jq is required. Install: brew install jq"; exit 1; }

echo "========================================"
echo "RabbitMQ Queue Monitor"
echo "========================================"
echo "Host: $RABBITMQ_HOST:$RABBITMQ_PORT"
echo "Output: $OUTPUT_FILE"
echo "Interval: ${INTERVAL}s"
echo ""

# Initialize CSV
echo "timestamp,elapsed_sec,total_messages,ready,unacked,publish_rate,consume_rate,avg_depth,max_depth" > "$OUTPUT_FILE"

START_TIME=$(date +%s)

# Cleanup on exit
cleanup() {
    echo -e "\n\n${GREEN}Monitoring stopped${NC}"
    echo "Data saved to: $OUTPUT_FILE"
    exit 0
}
trap cleanup INT TERM

while true; do
    # Get queue data
    RESPONSE=$(curl -s -u "$RABBITMQ_USER:$RABBITMQ_PASS" \
        "http://$RABBITMQ_HOST:$RABBITMQ_PORT/api/queues" 2>/dev/null || echo "[]")
    
    if [ "$RESPONSE" = "[]" ]; then
        echo -e "${RED}Error: Cannot connect to RabbitMQ Management API${NC}"
        sleep "$INTERVAL"
        continue
    fi
    
    # Filter room queues and calculate metrics
    ROOM_DATA=$(echo "$RESPONSE" | jq '[.[] | select(.name | startswith("room."))]')
    
    if [ "$(echo "$ROOM_DATA" | jq 'length')" -eq 0 ]; then
        echo "No room queues found"
        sleep "$INTERVAL"
        continue
    fi
    
    # Calculate metrics
    TOTAL_MESSAGES=$(echo "$ROOM_DATA" | jq '[.[].messages] | add // 0')
    TOTAL_READY=$(echo "$ROOM_DATA" | jq '[.[].messages_ready] | add // 0')
    TOTAL_UNACKED=$(echo "$ROOM_DATA" | jq '[.[].messages_unacknowledged] | add // 0')
    
    PUBLISH_RATE=$(echo "$ROOM_DATA" | jq '[.[].message_stats.publish_details.rate // 0] | add')
    CONSUME_RATE=$(echo "$ROOM_DATA" | jq '[.[].message_stats.deliver_get_details.rate // 0] | add')
    
    MAX_DEPTH=$(echo "$ROOM_DATA" | jq '[.[].messages] | max // 0')
    AVG_DEPTH=$(echo "$ROOM_DATA" | jq '[.[].messages] | add / length // 0')
    MAX_QUEUE=$(echo "$ROOM_DATA" | jq -r "[.[] | select(.messages == $MAX_DEPTH)][0].name // \"unknown\"")
    
    # Calculate elapsed time
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    # Print metrics
    echo ""
    echo "======================================================================"
    printf "RabbitMQ Metrics - %ds elapsed\n" "$ELAPSED"
    echo "======================================================================"
    printf "Total Messages:     %'d\n" "$TOTAL_MESSAGES"
    printf "Ready:              %'d\n" "$TOTAL_READY"
    printf "Unacknowledged:     %'d\n" "$TOTAL_UNACKED"
    printf "Publish Rate:       %.2f msg/s\n" "$PUBLISH_RATE"
    printf "Consume Rate:       %.2f msg/s\n" "$CONSUME_RATE"
    printf "Max Queue Depth:    %'d (%s)\n" "$MAX_DEPTH" "$MAX_QUEUE"
    echo "======================================================================"
    
    # Warnings
    if [ "$MAX_DEPTH" -gt 1000 ]; then
        echo -e "${YELLOW}⚠️  WARNING: Queue depth $MAX_DEPTH > 1000 (target)${NC}"
    fi
    
    # Check if publish rate > consume rate * 1.2 (using bc for floating point)
    if command -v bc >/dev/null 2>&1; then
        RATE_CHECK=$(echo "$PUBLISH_RATE > $CONSUME_RATE * 1.2" | bc -l 2>/dev/null || echo "0")
        if [ "$RATE_CHECK" -eq 1 ]; then
            echo -e "${YELLOW}⚠️  WARNING: Consumers falling behind (pub: $PUBLISH_RATE, con: $CONSUME_RATE)${NC}"
        fi
    fi
    
    # Write to CSV
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    echo "$TIMESTAMP,$ELAPSED,$TOTAL_MESSAGES,$TOTAL_READY,$TOTAL_UNACKED,$PUBLISH_RATE,$CONSUME_RATE,$AVG_DEPTH,$MAX_DEPTH" >> "$OUTPUT_FILE"
    
    sleep "$INTERVAL"
done
