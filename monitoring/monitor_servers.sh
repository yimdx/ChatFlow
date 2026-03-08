#!/bin/bash
# System Metrics Monitor
# Run this ON each server instance to monitor system resources
# Pure bash - no external dependencies

set -euo pipefail

# Configuration
INTERVAL="${INTERVAL:-5}"
OUTPUT_FILE="${OUTPUT_FILE:-system_metrics.csv}"
SERVER_ID="${SERVER_ID:-server-$(hostname)}"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "========================================"
echo "System Metrics Monitor"
echo "========================================"
echo "Server: $SERVER_ID"
echo "Output: $OUTPUT_FILE"
echo "Interval: ${INTERVAL}s"
echo ""

# Initialize CSV
echo "timestamp,elapsed_sec,cpu_percent,mem_used_mb,mem_total_mb,mem_percent,net_rx_kb,net_tx_kb,disk_read_kb,disk_write_kb" > "$OUTPUT_FILE"

START_TIME=$(date +%s)

# Cleanup on exit
cleanup() {
    echo -e "\n\n${GREEN}Monitoring stopped${NC}"
    echo "Data saved to: $OUTPUT_FILE"
    exit 0
}
trap cleanup INT TERM

# Get CPU usage (average over interval)
get_cpu_usage() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        top -l 2 -n 0 -s "$INTERVAL" | grep "CPU usage" | tail -1 | awk '{print 100-$7}' | cut -d. -f1
    else
        # Linux
        top -bn2 -d "$INTERVAL" | grep "Cpu(s)" | tail -1 | awk '{print 100-$8}' | cut -d. -f1
    fi
}

# Get memory usage
get_memory_usage() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        vm_stat | awk '
            /Pages active/ {active=$3}
            /Pages wired/ {wired=$4}
            /Pages occupied/ {occupied=$5}
            END {
                page_size=4096
                used=(active+wired+occupied)*page_size/1024/1024
                print int(used)
            }'
    else
        # Linux
        free -m | awk '/Mem:/ {print $3}'
    fi
}

get_memory_total() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sysctl -n hw.memsize | awk '{print int($1/1024/1024)}'
    else
        # Linux
        free -m | awk '/Mem:/ {print $2}'
    fi
}

# Get network I/O (KB)
get_network_io() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        netstat -ibn | awk '
            /en0/ && !/Link/ {
                rx+=$7; tx+=$10
            }
            END {
                printf "%d %d", rx/1024, tx/1024
            }'
    else
        # Linux
        cat /proc/net/dev | awk '
            /eth0|ens|enp/ {
                rx+=$2; tx+=$10
            }
            END {
                printf "%d %d", rx/1024, tx/1024
            }'
    fi
}

# Get disk I/O (KB) - macOS doesn't easily support this, Linux only
get_disk_io() {
    if [[ "$OSTYPE" == "linux"* ]] && [ -f /proc/diskstats ]; then
        cat /proc/diskstats | awk '
            /sda|xvda|nvme/ {
                read+=$6*512/1024
                write+=$10*512/1024
            }
            END {
                printf "%d %d", read, write
            }'
    else
        echo "0 0"
    fi
}

# Store previous network values for rate calculation
prev_net_rx=0
prev_net_tx=0
prev_disk_read=0
prev_disk_write=0

while true; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    # Collect metrics
    CPU=$(get_cpu_usage 2>/dev/null || echo "0")
    MEM_USED=$(get_memory_usage 2>/dev/null || echo "0")
    MEM_TOTAL=$(get_memory_total 2>/dev/null || echo "1")
    MEM_PERCENT=$(awk "BEGIN {printf \"%.1f\", ($MEM_USED/$MEM_TOTAL)*100}")
    
    # Network I/O
    read net_rx net_tx <<< $(get_network_io 2>/dev/null || echo "0 0")
    net_rx_rate=$((net_rx - prev_net_rx))
    net_tx_rate=$((net_tx - prev_net_tx))
    prev_net_rx=$net_rx
    prev_net_tx=$net_tx
    
    # Disk I/O
    read disk_read disk_write <<< $(get_disk_io 2>/dev/null || echo "0 0")
    disk_read_rate=$((disk_read - prev_disk_read))
    disk_write_rate=$((disk_write - prev_disk_write))
    prev_disk_read=$disk_read
    prev_disk_write=$disk_write
    
    # Print metrics
    echo ""
    echo "======================================================================"
    printf "System Metrics - %ds elapsed\n" "$ELAPSED"
    echo "======================================================================"
    printf "CPU Usage:        %3d%%\n" "$CPU"
    printf "Memory:           %d MB / %d MB (%.1f%%)\n" "$MEM_USED" "$MEM_TOTAL" "$MEM_PERCENT"
    printf "Network RX:       %d KB/s\n" "$net_rx_rate"
    printf "Network TX:       %d KB/s\n" "$net_tx_rate"
    printf "Disk Read:        %d KB/s\n" "$disk_read_rate"
    printf "Disk Write:       %d KB/s\n" "$disk_write_rate"
    echo "======================================================================"
    
    # Warnings
    if [ "$CPU" -gt 80 ]; then
        echo -e "${RED}⚠️  WARNING: CPU usage > 80%${NC}"
    fi
    if [ "$(echo "$MEM_PERCENT > 85" | bc -l 2>/dev/null || echo 0)" -eq 1 ]; then
        echo -e "${YELLOW}⚠️  WARNING: Memory usage > 85%${NC}"
    fi
    
    # Write to CSV
    echo "$TIMESTAMP,$ELAPSED,$CPU,$MEM_USED,$MEM_TOTAL,$MEM_PERCENT,$net_rx_rate,$net_tx_rate,$disk_read_rate,$disk_write_rate" >> "$OUTPUT_FILE"
    
    sleep "$INTERVAL"
done
