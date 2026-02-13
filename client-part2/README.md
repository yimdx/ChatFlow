# ChatFlow Client - Part 2 (Performance Analysis)

## Overview

This is Part 2 of the ChatFlow client implementation, which includes comprehensive performance analysis capabilities:

- Per-message latency tracking
- CSV output with detailed metrics
- Statistical analysis (mean, median, percentiles)
- Throughput visualization over time
- Message type and room distribution analysis

## Requirements

- Java 17 or higher
- Maven 3.6+
- Running ChatFlow server (see server/ directory)

## Building

```bash
mvn clean package
```

This will create an executable JAR file in the `target/` directory.

## Running

### Method 1: Using Maven

```bash
mvn exec:java -Dexec.mainClass="cs6650.assignment1.Main"
```

### Method 2: Using the JAR

```bash
java -jar target/client-part2-1.0-SNAPSHOT.jar
```

## Configuration

Before running, update the `SERVER_URL` in `Main.java`:

```java
private static final String SERVER_URL = "ws://your-server-url:8080";
```

For local testing:
```java
private static final String SERVER_URL = "ws://localhost:8080";
```

For AWS EC2 deployment:
```java
private static final String SERVER_URL = "ws://ec2-xx-xxx-xxx-xxx.us-west-2.compute.amazonaws.com:8080";
```

## Features

### 1. Per-Message Metrics

Each message is tracked with:
- Timestamp (when sent)
- Message type (TEXT, JOIN, LEAVE)
- Latency (milliseconds)
- Status code (success, error, timeout)
- Room ID

All metrics are written to a CSV file in the `results/` directory.

### 2. Statistical Analysis

Upon completion, the client calculates and displays:
- Mean response time
- Median response time
- 95th percentile response time
- 99th percentile response time
- Min/max response times
- Message type distribution
- Throughput per room

### 3. Throughput Visualization

The client generates:
- A visual chart (GUI) showing throughput over time
- A text file with throughput data for further analysis

## Output Files

All results are saved in the `results/` directory:

- `metrics_YYYYMMDD_HHMMSS.csv` - Detailed per-message metrics
- `throughput_YYYYMMDD_HHMMSS.txt` - Throughput data over time

### CSV Format

```
timestamp,messageType,latency,statusCode,roomId
1707753600000,TEXT,45,success,5
1707753600123,JOIN,32,success,12
...
```

## Performance Testing

The client sends 500,000 messages in two phases:

1. **Warmup Phase**: 32 threads × 1,000 messages = 32,000 messages
2. **Main Phase**: Optimized thread count × remaining messages = 468,000 messages

## Architecture

### Key Classes

- **Main**: Orchestrates the entire testing process
- **MessageGenerator**: Generates random chat messages
- **MessageSender**: Sends messages via WebSocket and tracks latency
- **ChatWebSocketClient**: Enhanced WebSocket client with async response tracking
- **MetricRecord**: Data class for per-message metrics
- **CsvWriter**: Writes metrics to CSV file asynchronously
- **PerformanceAnalyzer**: Calculates statistical metrics
- **ThroughputVisualizer**: Creates throughput charts

### Threading Model

- 1 thread for message generation
- 1 thread for CSV writing
- 32 threads for warmup phase
- N threads for main phase (4 × CPU cores)

## Example Output

```
========================================
BASIC PERFORMANCE RESULTS
========================================
1. Successful messages sent: 498,523
2. Failed messages: 1,477
3. Total runtime: 125,432 ms (125.432 seconds)
   - Warmup phase: 8,234 ms
   - Main phase: 115,198 ms
4. Overall throughput: 3,986.45 messages/second
   - Warmup throughput: 3,886.23 messages/second
   - Main phase throughput: 4,063.12 messages/second
5. Connection statistics:
   - Total connections: 48
   - Reconnections: 3

========================================
STATISTICAL ANALYSIS
========================================
Total Messages: 498,523
Mean Response Time: 42.35 ms
Median Response Time: 38.00 ms
95th Percentile: 78.00 ms
99th Percentile: 125.00 ms
Min Response Time: 12 ms
Max Response Time: 2,345 ms

Message Type Distribution:
  TEXT: 448,671 (90.0%)
  JOIN: 24,926 (5.0%)
  LEAVE: 24,926 (5.0%)

Throughput Per Room:
  Room 1: 24,935 messages
  Room 2: 24,891 messages
  ...
========================================
```

## Troubleshooting

### Connection Issues

If you see connection errors:
1. Verify the server is running
2. Check the SERVER_URL is correct
3. Ensure firewall allows WebSocket connections

### Out of Memory

If you encounter memory issues:
```bash
java -Xmx4g -jar target/client-part2-1.0-SNAPSHOT.jar
```

### Slow Performance

- Ensure the server is on a fast network connection
- Check server logs for bottlenecks
- Verify CPU and memory resources are adequate

## Comparison with Part 1

Part 2 adds:
- ✅ Per-message latency tracking
- ✅ CSV output with detailed metrics
- ✅ Statistical analysis
- ✅ Throughput visualization
- ✅ Message type and room distribution

Part 1 provided only basic throughput metrics.

## License

CS6650 Assignment 1 - Educational purposes only
