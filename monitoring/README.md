# External Monitoring Tools - Assignment 2 Part 4

**Pure Bash Scripts - No Python Required!**

External monitoring scripts that observe your system through RabbitMQ Management API and health endpoints. No code changes needed!

## Quick Start

### 1. Install Dependencies
```bash
# macOS
brew install jq

# Ubuntu/Debian  
sudo apt-get install jq curl

# Note: curl is already included on macOS
```

### 2. Enable RabbitMQ Management Plugin
```bash
# If using Docker
docker exec rabbitmq rabbitmq-plugins enable rabbitmq_management

# Verify it's accessible
curl http://localhost:15672/api/overview -u admin:adminpassword
```

### 3. Run Monitoring
```bash
# Make scripts executable
cd monitoring
chmod +x *.sh

# Simple monitoring
./run_monitoring.sh

# With custom settings
RABBITMQ_HOST=3.238.247.90 \
RABBITMQ_USER=admin \
RABBITMQ_PASS=adminpassword \
INTERVAL=5 \
OUTPUT_DIR=results \
./run_monitoring.sh
```

### 4. Run Your Test
In another terminal:
```bash
cd client-part2
java -jar target/client-part2-1.0-SNAPSHOT.jar ws://your-server:8081
```

### 5. Stop and Generate Report
Press `Ctrl+C` in the monitoring terminal. It will automatically:
- Stop monitors
- Generate statistical analysis
- Create plots (if gnuplot installed)

## Scripts

### monitor_rabbitmq.sh
Monitors RabbitMQ queues via Management API (pure bash).

**Usage:**
```bash
./monitor_rabbitmq.sh
# Or with environment variables:
RABBITMQ_HOST=localhost \
RABBITMQ_PORT=15672 \
RABBITMQ_USER=admin \
RABBITMQ_PASS=adminpassword \
INTERVAL=5 \
OUTPUT_FILE=queue_metrics.csv \
./monitor_rabbitmq.sh
```

**Tracks:**
- Queue depth (ready, unacked, total)
- Publish/consume rates (msg/s)
- Per-room statistics

**Alerts:**
- ⚠️ Queue depth > 1000
- ⚠️ Consumers falling behind  
- ⚠️ High unacked message ratio

### monitor_servers.sh
Monitors system metrics (CPU, memory, network, disk I/O). **Run this ON each server instance**.

**Usage:**
```bash
# Run on server instance
./monitor_servers.sh

# Or with environment variables:
INTERVAL=5 \
OUTPUT_FILE=system_metrics.csv \
SERVER_ID=server-1 \
./monitor_servers.sh
```

**Tracks:**
- CPU usage (%)
- Memory usage (MB and %)
- Network I/O (RX/TX KB/s)
- Disk I/O (Read/Write KB/s - Linux only)

**Alerts:**
- ⚠️ CPU > 80%
- ⚠️ Memory > 85%

### analyze_metrics.sh
Generates statistics and plots (pure bash with optional gnuplot).

**Usage:**
```bash
./analyze_metrics.sh queue_metrics.csv
```

**Generates:**
- Statistical analysis (always)
- Queue depth plot (if gnuplot installed)
- Throughput plot (if gnuplot installed)

**Optional: Install gnuplot for plots**
```bash
brew install gnuplot
```

## Test Scenarios

### Single Server Baseline
```bash
# Terminal 1: Monitor
./run_monitoring.sh

# Terminal 2: Test
java -jar client-part2/target/client-part2-1.0-SNAPSHOT.jar ws://server:8081

# Stop monitoring when done (Ctrl+C)
# Results in: monitoring_results/
```

### Load Balanced (2 Servers)
```bash
# Terminal 1: Monitor RabbitMQ (from local machine)
RABBITMQ_HOST=your-mq-host ./run_monitoring.sh

# Terminal 2: On each server, monitor system metrics
ssh server1
cd monitoring
./monitor_servers.sh &

ssh server2
cd monitoring
./monitor_servers.sh &
Terminal 1: Monitor RabbitMQ (from local machine)
RABBITMQ_HOST=3.238.247.90 \
RABBITMQ_USER=admin \
RABBITMQ_PASS=adminpassword \
OUTPUT_DIR=aws_test_results \
./run_monitoring.sh

# Terminal 2: SSH to each EC2 server and monitor system metrics
ssh ec2-server1
cd /path/to/monitoring
SERVER_ID=server-1 ./monitor_servers.sh > server1_metrics.log 2>&1 &

ssh ec2-server2
cd /path/to/monitoring
SERVER_ID=server-2 ./monitor_servers.sh > server2_metrics.log 2>&1 &

# Terminal 3: R2
RABBITMQ_HOST=3.238.247.90 \
RABBITMQ_USER=admin \
RABBITMQ_PASS=adminpassword \
OUTPUT_DIR=aws_test_results \
./run_monitoring.sh

# Inystem_metrics_TIMESTAMP.csv` - System metrics (CPU, memory, network, disk)
java -jar client-part2/target/client-part2-1.0-SNAPSHOT.jar \
    ws://chatflow-1572582138.us-east-1.elb.amazonaws.com:8081
```

## Output Files

### CSV Files
- `queue_metrics_TIMESTAMP.csv` - RabbitMQ metrics over time
- `server_health_TIMESTAMP.csv` - Server availability

### Plots (if gnuplot installed)
- `plots/queue_depth.png` - Queue depth vs target
- `plots/throughput.png` - Publish/consume rates

### Statistics
Console output shows:
- Duration
- Peak/average queue depth
- Throughput rates
- Consumer efficiency
- Target compliance (< 1000 depth)

## Performance Targets

From Assignment Spec Part 4:

✅ **Queue depth < 1000 consistently**  
✅ **Consumer lag < 100ms**  
✅ **No message loss**  
✅ **Stable throughput**

## Troubleshooting

### Cannot connect to RabbitMQ Management API
```bash
# Check if management plugin is enabled
docker exec rabbitmq rabbitmq-plugins list

# Enable if needed
docker exec rabbitmq rabbitmq-plugins enable rabbitmq_management

# Test connection
curl http://localhost:15672/api/overview -u admin:adminpassword
```

### jq not found
```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get install jq
```

### High queue depth
Indicates consumers can't keep up. Try:
- Increase `CONSUMER_THREAD_COUNT`
- Add more consumer instances
- Optimize message processing
- Check for network bottlenecks

### Consumers falling behind
When publish rate > consume rate:
- Scale consumers horizontally
- Optimize consumer code
- Check HTTP broadcast timeout
- Review network latency

## Example Output

```
======================================================================
RabbitMQ Metrics - 120s elapsed
======================================================================
Total Messages:     15,234
Ready:              892
Unacknowledged:     245
Publish Rate:       145.32 msg/s
Consume Rate:       148.67 msg/s
Max Queue Depth:    892 (room.5)
======================================================================

PERFORMANCE ANALYSIS
======================================================================
Duration: 300.45 seconds (5.0 minutes)

Queue Depth:
  Peak:    1234
  Average: 645.23
  Status:  ✗ Exceeded target by 234

Throughput:
  Avg Publish: 167.45 msg/s
  Peak Publish: 234.12 msg/s
  Avg Consume: 165.89 msg/s
  Peak Consume: 245.67 msg/s

Consumer Efficiency: 99.1%
  ✓ Consumers keeping up well

Total Messages Processed: ~15234
======================================================================
```

## Assignment Submission

Include in your report:

1. **CSV Files** - Raw metrics data
2. **Statistics** - Console output from analyze_metrics.sh
3. **Plots** - If gnuplot installed
4. **Screenshots** - RabbitMQ Management Console
5. **Analysis** - Compare 1, 2, 4 server configurations
6. **Observations** - Bottlenecks and optimizations

## Notes

- **100% Bash** - No Python dependencies
- **Minimal requirements** - Just curl and jq
- **Real-time alerts** - Console warnings for issues
- **Automatic cleanup** - Ctrl+C generates final report
- **Production-ready** - Use for actual deployment monitoring

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│   Server    │────▶│  RabbitMQ   │
└─────────────┘     └─────────────┘     └─────────────┘
                           │                    │
                           ▼                    ▼
                    ┌─────────────┐     ┌─────────────┐
                    │ /health     │     │ Management  │
                    │ endpoint    │     │    API      │
                    └─────────────┘     └─────────────┘
                           │                    │
                           └────────┬───────────┘
                                    ▼
                           ┌─────────────────┐
                           │  Bash Scripts   │
                           │   (curl + jq)   │
                           └─────────────────┘
```



