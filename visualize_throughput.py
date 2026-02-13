#!/usr/bin/env python3
"""
Throughput Visualization Script for ChatFlow
Reads throughput data files and generates visualization charts.
"""

import matplotlib.pyplot as plt
import sys
import os
from datetime import datetime


def parse_throughput_file(filepath):
    """Parse throughput data from text file."""
    times = []
    throughputs = []
    
    with open(filepath, 'r') as f:
        for line in f:
            line = line.strip()
            # Skip header lines
            if not line or '=' in line or 'Time' in line or 'Messages' in line:
                continue
            
            parts = line.split(',')
            if len(parts) == 2:
                try:
                    time = float(parts[0].strip())
                    throughput = float(parts[1].strip())
                    times.append(time)
                    throughputs.append(throughput)
                except ValueError:
                    continue
    
    return times, throughputs


def create_throughput_chart(times, throughputs, title="ChatFlow Throughput Over Time"):
    """Create and display throughput visualization."""
    plt.figure(figsize=(12, 6))
    
    # Main plot
    plt.plot(times, throughputs, 'b-', linewidth=1.5, label='Throughput')
    
    # Add average line
    avg_throughput = sum(throughputs) / len(throughputs)
    plt.axhline(y=avg_throughput, color='r', linestyle='--', 
                linewidth=1, label=f'Average: {avg_throughput:.2f} msg/s')
    
    # Styling
    plt.xlabel('Time (seconds)', fontsize=12)
    plt.ylabel('Throughput (messages/second)', fontsize=12)
    plt.title(title, fontsize=14, fontweight='bold')
    plt.grid(True, alpha=0.3)
    plt.legend(loc='best')
    
    # Add statistics box
    max_throughput = max(throughputs)
    min_throughput = min(throughputs)
    stats_text = f'Max: {max_throughput:.2f} msg/s\nMin: {min_throughput:.2f} msg/s\nAvg: {avg_throughput:.2f} msg/s'
    plt.text(0.02, 0.98, stats_text, transform=plt.gca().transAxes,
             fontsize=10, verticalalignment='top',
             bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))
    
    plt.tight_layout()
    return plt


def save_chart(plt_obj, output_path):
    """Save chart to file."""
    plt_obj.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Chart saved to: {output_path}")


def main():
    if len(sys.argv) < 2:
        print("Usage: python visualize_throughput.py <throughput_file.txt> [output_image.png]")
        print("\nExample:")
        print("  python visualize_throughput.py throughput_20260213_140011.txt")
        print("  python visualize_throughput.py throughput_20260213_140011.txt output.png")
        sys.exit(1)
    
    input_file = sys.argv[1]
    
    if not os.path.exists(input_file):
        print(f"Error: File not found: {input_file}")
        sys.exit(1)
    
    print(f"Reading throughput data from: {input_file}")
    times, throughputs = parse_throughput_file(input_file)
    
    if not times:
        print("Error: No valid data found in file")
        sys.exit(1)
    
    print(f"Loaded {len(times)} data points")
    print(f"Time range: {times[0]:.1f}s to {times[-1]:.1f}s")
    print(f"Throughput range: {min(throughputs):.2f} to {max(throughputs):.2f} msg/s")
    
    # Create chart
    plt_obj = create_throughput_chart(times, throughputs)
    
    # Save or display
    if len(sys.argv) >= 3:
        output_file = sys.argv[2]
        save_chart(plt_obj, output_file)
    else:
        # Auto-generate output filename
        base_name = os.path.splitext(os.path.basename(input_file))[0]
        output_file = f"{base_name}_chart.png"
        save_chart(plt_obj, output_file)
    
    # Display chart
    print("Displaying chart (close window to exit)...")
    plt_obj.show()


if __name__ == "__main__":
    main()
