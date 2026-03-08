#!/bin/bash
# Metrics Analysis Script
# Pure bash - generates simple text reports
# Optional: use gnuplot for visualizations

set -euo pipefail

if [ $# -eq 0 ]; then
    echo "Usage: $0 <metrics.csv>"
    echo "Generates statistical analysis from CSV metrics"
    exit 1
fi

INPUT_FILE="$1"

if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: File not found: $INPUT_FILE"
    exit 1
fi

# Check if gnuplot is available
HAVE_GNUPLOT=false
if command -v gnuplot >/dev/null 2>&1; then
    HAVE_GNUPLOT=true
fi

echo "========================================"
echo "METRICS ANALYSIS"
echo "========================================"
echo "Input: $INPUT_FILE"
echo ""

# Skip header, get statistics
tail -n +2 "$INPUT_FILE" > /tmp/metrics_data.tmp

# Calculate statistics using awk
awk -F',' '
BEGIN {
    max_depth = 0
    sum_depth = 0
    sum_pub = 0
    sum_con = 0
    count = 0
    max_pub = 0
    max_con = 0
}
{
    # Fields: timestamp,elapsed_sec,total_messages,ready,unacked,publish_rate,consume_rate,avg_depth,max_depth
    if (NF >= 9) {
        elapsed = $2
        total = $3
        ready = $4
        unacked = $5
        pub_rate = $6
        con_rate = $7
        avg_depth = $8
        max_q = $9
        
        if (max_q > max_depth) max_depth = max_q
        sum_depth += avg_depth
        sum_pub += pub_rate
        sum_con += con_rate
        if (pub_rate > max_pub) max_pub = pub_rate
        if (con_rate > max_con) max_con = con_rate
        count++
        
        final_elapsed = elapsed
        final_total = total
    }
}
END {
    printf "Duration: %.2f seconds (%.1f minutes)\n", final_elapsed, final_elapsed/60
    printf "\n"
    printf "Queue Depth:\n"
    printf "  Peak:    %d\n", max_depth
    printf "  Average: %.2f\n", sum_depth/count
    
    if (max_depth < 1000) {
        printf "  Status:  ✓ Within target (< 1000)\n"
    } else {
        printf "  Status:  ✗ Exceeded target by %d\n", max_depth - 1000
    }
    
    printf "\n"
    printf "Throughput:\n"
    printf "  Avg Publish: %.2f msg/s\n", sum_pub/count
    printf "  Peak Publish: %.2f msg/s\n", max_pub
    printf "  Avg Consume: %.2f msg/s\n", sum_con/count
    printf "  Peak Consume: %.2f msg/s\n", max_con
    
    printf "\n"
    avg_pub = sum_pub/count
    avg_con = sum_con/count
    efficiency = (avg_pub > 0) ? (avg_con / avg_pub * 100) : 0
    printf "Consumer Efficiency: %.1f%%\n", efficiency
    
    if (efficiency < 80) {
        printf "  ✗ Consumers falling behind - consider scaling\n"
    } else if (efficiency > 120) {
        printf "  ✓ Consumers keeping up well\n"
    } else {
        printf "  ~ Consumers roughly matching producers\n"
    }
    
    printf "\n"
    printf "Total Messages Processed: ~%d\n", final_total
}
' /tmp/metrics_data.tmp

echo "========================================"

# Generate gnuplot if available
if [ "$HAVE_GNUPLOT" = true ]; then
    OUTPUT_DIR="plots"
    mkdir -p "$OUTPUT_DIR"
    
    echo ""
    echo "Generating plots with gnuplot..."
    
    # Queue depth plot
    gnuplot <<-EOF 2>/dev/null
		set terminal png size 1200,800
		set output '$OUTPUT_DIR/queue_depth.png'
		set datafile separator ','
		set title 'Queue Depth Over Time' font ',14'
		set xlabel 'Time (seconds)'
		set ylabel 'Queue Depth'
		set grid
		plot '$INPUT_FILE' using 2:9 with lines lw 2 title 'Max Depth', \
		     1000 with lines lw 2 lc rgb 'red' title 'Target (1000)'
	EOF
    
    # Throughput plot
    gnuplot <<-EOF 2>/dev/null
		set terminal png size 1200,800
		set output '$OUTPUT_DIR/throughput.png'
		set datafile separator ','
		set title 'Message Throughput' font ',14'
		set xlabel 'Time (seconds)'
		set ylabel 'Rate (msg/s)'
		set grid
		plot '$INPUT_FILE' using 2:6 with lines lw 2 title 'Publish Rate', \
		     '$INPUT_FILE' using 2:7 with lines lw 2 title 'Consume Rate'
	EOF
    
    echo "✓ Plots saved to: $OUTPUT_DIR/"
    echo "  - queue_depth.png"
    echo "  - throughput.png"
else
    echo ""
    echo "Note: Install gnuplot for visualizations: brew install gnuplot"
fi

# Cleanup
rm -f /tmp/metrics_data.tmp

echo ""
echo "Analysis complete!"
