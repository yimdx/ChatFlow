# Assignment 2 Submission Checklist

**Due: March 8, 2026, 5PM PST**

## Repository Structure

- [x] `/server-v2` - Updated server with queue integration
- [x] `/consumer` - Consumer application  
- [x] `/client-part1` - Part 1 client
- [x] `/client-part2` - Part 2 client
- [ ] `/deployment` - ALB configuration, scripts
- [x] `/monitoring` - Monitoring scripts and tools

## 1. Architecture Document (2 pages max)

### Required Diagrams and Descriptions

- [ ] **System Architecture Diagram**
  - Components: Client → ALB → Servers → RabbitMQ → Consumer → Servers
  - Show all connections and data flows
  - Label ports (8080 health, 8081 WebSocket, 8082 broadcast)
  
- [ ] **Message Flow Sequence Diagram**
  - Client sends message to server via WebSocket
  - Server publishes to RabbitMQ (room queue)
  - Consumer receives from RabbitMQ
  - Consumer broadcasts to all servers via HTTP
  - Servers broadcast to connected clients in room

- [ ] **Queue Topology Design**
  - Exchange: `chat.exchange` (topic)
  - Queues: `room.1` through `room.20`
  - Routing keys: `room.{roomId}`
  - Explain why HTTP broadcast instead of fanout exchange

- [ ] **Consumer Threading Model**
  - Number of consumer threads (default: 20)
  - Room distribution strategy (fair distribution)
  - Message processing pipeline
  - Thread safety mechanisms (ConcurrentHashMap)

- [ ] **Load Balancing Configuration**
  - ALB setup and target groups
  - Health check configuration
  - Sticky sessions for WebSocket
  - Connection distribution

- [ ] **Failure Handling Strategies**
  - Server failure: ALB removes from target group
  - RabbitMQ failure: Retry logic, channel pooling
  - Consumer failure: Auto-restart, message requeue
  - Network timeout: HTTP client timeout configuration

## 2. Test Results

### Single Instance Tests

- [ ] **Client Output Screenshot**
  - Total messages sent (500K)
  - Total runtime
  - Messages per second
  - Connection failures (if any)
  - Sample output from console

- [ ] **RabbitMQ Management Console Screenshots**
  - Queue depths over time (graphs)
  - Message rates: publish rate vs consume rate
  - Connection details: active connections, channels
  - Queue statistics for all 20 room queues

### Load Balanced Tests (2 Instances)

- [ ] **Client Output**
  - 500K messages throughput
  - Compare with single instance baseline
  - Show improvement percentage

- [ ] **ALB Metrics**
  - Request distribution across 2 instances
  - Active connection count per instance
  - Health check status
  - Target response time

- [ ] **Queue Metrics**
  - Queue depth comparison (single vs 2 instances)
  - Consumer lag metrics
  - Message processing rate improvement

### Load Balanced Tests (4 Instances)

- [ ] **Client Output**
  - 500K messages throughput
  - 1M messages stress test (optional)
  - Maximum throughput achieved

- [ ] **Performance Analysis**
  - Throughput improvement: single → 2 → 4 instances
  - Scalability curve
  - Identify bottlenecks (if any)
  - Resource utilization trends

### Monitoring Data

- [ ] **Queue Monitoring CSV Files**
  - `queue_metrics_TIMESTAMP.csv`
  - Plots generated from monitoring data (if gnuplot available)

- [ ] **System Metrics CSV Files** (from each server)
  - `system_metrics_TIMESTAMP.csv` per server
  - CPU usage trends
  - Memory usage trends
  - Network I/O statistics

## 3. Configuration Details

### Queue Configuration Parameters

Document in submission:

- [ ] **RabbitMQ Configuration**
  - Exchange name: `chat.exchange`
  - Exchange type: `topic`
  - Queue names: `room.1` through `room.20`
  - Durable: Yes
  - Auto-delete: No
  - Message TTL: (specify if configured)
  - Queue max length: (specify if configured)

- [ ] **Connection Pool Settings**
  - Pool size: 20 channels
  - Connection timeout: (specify)
  - Heartbeat interval: (specify)

### Consumer Configuration

- [ ] **Consumer Settings**
  - Number of threads: 20 (default)
  - Prefetch count: 1 per consumer
  - Acknowledgment mode: Manual
  - Requeue on failure: Yes/No

- [ ] **HTTP Broadcast Configuration**
  - Server URLs: List all server endpoints
  - Connect timeout: 5 seconds
  - Request timeout: 3 seconds
  - Retry logic: (describe if implemented)

### ALB Settings

- [ ] **Target Group Configuration**
  - Protocol: HTTP
  - Port: 8081 (WebSocket)
  - Health check path: `/health`
  - Health check interval: 30s
  - Timeout: 5s
  - Healthy threshold: 2
  - Unhealthy threshold: 3

- [ ] **Load Balancer Configuration**
  - Sticky sessions: Enabled
  - Session cookie: AWSALB
  - Idle timeout: 60+ seconds
  - WebSocket support: Enabled

### Instance Types Used

- [ ] **Server Instances**
  - Instance type: (e.g., t2.medium, t3.large)
  - vCPUs: (specify)
  - Memory: (specify)
  - Number of instances tested: 1, 2, 4

- [ ] **RabbitMQ Instance**
  - Instance type: (e.g., t2.medium)
  - Configuration: (memory, disk)

- [ ] **Consumer Instance**
  - Instance type: (e.g., t2.medium)
  - Configuration: (vCPUs, memory)

## 4. Grading Rubric Self-Check

### Queue Integration (15 points)

- [ ] **Correct message publishing (5 points)**
  - Messages published to correct room queue
  - Proper QueueMessage format with all fields
  - Publisher confirms enabled
  - Channel pooling implemented

- [ ] **Proper consumer implementation (5 points)**
  - Multi-threaded consumer pool (20 threads)
  - Fair room distribution
  - At-least-once delivery with manual ACK
  - HTTP broadcast to all servers

- [ ] **Error handling and recovery (3 points)**
  - Try-catch blocks for all operations
  - Channel pool recovery on error
  - HTTP timeout handling
  - Logging of errors

- [ ] **Clean separation of concerns (2 points)**
  - Separate packages: config, controller, model, queue
  - Clear class responsibilities
  - No business logic in Main class
  - Reusable components

### System Design (10 points)

- [ ] **Clear architecture documentation**
  - All diagrams present and clear
  - Explanations provided
  - Design decisions justified

- [ ] **Appropriate design patterns**
  - Channel pooling for RabbitMQ
  - HTTP callback pattern for broadcasting
  - Thread-safe data structures

- [ ] **Scalability considerations**
  - Horizontal scaling with ALB
  - Configurable thread counts
  - Queue-based decoupling

### Single Instance Performance (10 points)

- [ ] **Achieves good throughput (5 points)**
  - Processes 500K messages efficiently
  - Measure and document msg/s
  - Compare with baseline expectations

- [ ] **Maintains stable queue depths (3 points)**
  - Queue depth < 1000 consistently
  - Show queue depth graphs
  - No extreme sawtooth patterns

- [ ] **Proper resource utilization (2 points)**
  - CPU usage reasonable (<80% sustained)
  - Memory usage stable
  - No resource leaks

### Load Balanced Performance (10 points)

- [ ] **Improved throughput over single instance (5 points)**
  - 2 instances: Show X% improvement
  - 4 instances: Show Y% improvement
  - Document throughput numbers

- [ ] **Even distribution across instances (3 points)**
  - ALB distributes connections evenly
  - Show distribution metrics/graphs
  - No single instance overloaded

- [ ] **Stable system under load (2 points)**
  - All instances healthy during test
  - No crashes or errors
  - Queue depths remain stable

### Bonus Points

- [ ] **Performance Competition**
  - Run tests with optimal configuration
  - Document exact throughput achieved
  - Ensure queue profile is stable (required for bonus)
  - Top 3: +2 points each
  - Next 3: +1 point each

## 5. Pre-Submission Checklist

### Code Quality

- [ ] All code compiles without errors
- [ ] No hardcoded values (use environment variables)
- [ ] Comments on complex logic
- [ ] Consistent code formatting
- [ ] No commented-out debug code

### Documentation

- [ ] README.md in each folder
  - `/server-v2/README.md`
  - `/consumer/README.md`
  - `/monitoring/README.md`
- [ ] Build instructions clear
- [ ] Deployment instructions documented
- [ ] Configuration parameters explained

### Git Repository

- [ ] All code pushed to repository
- [ ] Repository is public or instructor has access
- [ ] No sensitive data (passwords, keys) committed
- [ ] Clean commit history
- [ ] Tag final submission: `git tag assignment2-final`

### Submission Package

- [ ] Convert to PDF (not just doc/markdown)
- [ ] All screenshots included and readable
- [ ] File size reasonable (<25MB)
- [ ] Submitted before deadline: **March 8, 2026, 5PM PST**

## 6. Useful Commands for Data Collection

### RabbitMQ Monitoring

```bash
# Start RabbitMQ monitoring
cd monitoring
./run_monitoring.sh

# Or with custom settings
RABBITMQ_HOST=3.238.247.90 \
RABBITMQ_USER=admin \
RABBITMQ_PASS=adminpassword \
./run_monitoring.sh
```

### System Metrics (on each server)

```bash
# SSH to server and run
ssh ec2-server
cd /path/to/monitoring
SERVER_ID=server-1 ./monitor_servers.sh > metrics.log 2>&1 &
```

### Run Client Tests

```bash
# Single server test
cd client-part2
java -jar target/client-part2-1.0-SNAPSHOT.jar ws://server:8081

# Load balanced test
java -jar target/client-part2-1.0-SNAPSHOT.jar ws://lb-dns:8081
```

### Analyze Metrics

```bash
cd monitoring
./analyze_metrics.sh queue_metrics_*.csv
```

### Get ALB Metrics

```bash
# Via AWS CLI
aws elbv2 describe-target-health \
  --target-group-arn <target-group-arn>

# View in AWS Console
# EC2 → Load Balancers → Your ALB → Monitoring tab
```

## 7. Common Issues to Avoid

- [ ] **Forgetting to enable RabbitMQ management plugin**
  ```bash
  docker exec rabbitmq rabbitmq-plugins enable rabbitmq_management
  ```

- [ ] **Not opening required ports in security groups**
  - 15672 (RabbitMQ Management)
  - 5672 (RabbitMQ AMQP)
  - 8080 (Health check)
  - 8081 (WebSocket)
  - 8082 (HTTP broadcast)

- [ ] **Screenshots not showing critical data**
  - Ensure timestamps visible
  - Show full terminal output
  - RabbitMQ console graphs clearly visible

- [ ] **Missing architecture justifications**
  - Explain why HTTP broadcast instead of fanout
  - Justify thread pool sizes
  - Explain queue topology choices

## Notes

- Save monitoring data during test runs
- Take screenshots immediately after tests
- Document any issues encountered and solutions
- Keep track of exact configurations used
- Test everything before submission deadline

---

**Final Check**: Review this entire checklist one day before submission to ensure nothing is missing!
