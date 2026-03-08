#!/bin/bash
# Server Health Monitoring Script
# Pure bash - no Python required
# Requires: curl

set -euo pipefail

# Configuration
INTERVAL="${INTERVAL:-10}"
OUTPUT_FILE="${OUTPUT_FILE:-server_health.csv}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check dependencies
command -v curl >/dev/null 2>&1 || { echo "Error: curl is required"; exit 1; }

if [ $# -eq 0 ]; then
    echo "Usage: $0 <server1:port> <server2:port> ..."
    echo "Example: $0 server1:8080 server2:8080 server3:8080"
    exit 1
fi

SERVERS=("$@")

echo "========================================"
echo "Server Health Monitor"
echo "========================================"
echo "Servers: ${SERVERS[*]}"
echo "Output: $OUTPUT_FILE"
echo "Interval: ${INTERVAL}s"
echo ""

# Initialize CSV
HEADER="timestamp,elapsed_sec"
for server in "${SERVERS[@]}"; do
    HEADER="$HEADER,${server}_status,${server}_response_ms"
done
echo "$HEADER" > "$OUTPUT_FILE"

START_TIME=$(date +%s)

# Cleanup on exit
cleanup() {
    echo -e "\n\n${GREEN}Monitoring stopped${NC}"
    echo "Data saved to: $OUTPUT_FILE"
    exit 0
}
trap cleanup INT TERM

check_server() {
    local server=$1
    local url="http://$server/health"
    
    # Use curl with timing (milliseconds)
    local start=$(gdate +%s%3N 2>/dev/null || date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    local status_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$url" 2>/dev/null || echo "000")
    local end=$(gdate +%s%3N 2>/dev/null || date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    local response_time=$((end - start))
    
    if [ "$status_code" = "200" ]; then
        echo "healthy|$response_time"
    elif [ "$status_code" = "000" ]; then
        echo "timeout|0"
    else
        echo "error_$status_code|0"
    fi
}

while true; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    echo ""
    echo "======================================================================"
    printf "Server Health Check - %ds elapsed\n" "$ELAPSED"
    echo "======================================================================"
    
    # Check all servers
    CSV_ROW="$TIMESTAMP,$ELAPSED"
    
    for server in "${SERVERS[@]}"; do
        result=$(check_server "$server")
        status="${result%%|*}"
        resp_time="${result##*|}"
        
        # Print status
        if [ "$status" = "healthy" ]; then
            printf "${GREEN}✓${NC} %-30s %-20s %10sms\n" "$server" "$status" "$resp_time"
        else
            printf "${RED}✗${NC} %-30s %-20s %10s\n" "$server" "$status" "N/A"
        fi
        
        # Add to CSV
        CSV_ROW="$CSV_ROW,$status,$resp_time"
    done
    
    echo "======================================================================"
    
    # Write to CSV
    echo "$CSV_ROW" >> "$OUTPUT_FILE"
    
    sleep "$INTERVAL"
done
