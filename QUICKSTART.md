# Quick Start Guide - Assignment 2

## Prerequisites
- Java 17 or higher
- Maven 3.6+
- RabbitMQ (local or remote)

## Local Testing (5 Minutes Setup)

### Step 1: Start RabbitMQ (macOS)
```bash
# Install RabbitMQ (if not installed)
brew install rabbitmq

# Start RabbitMQ
brew services start rabbitmq

# Enable management plugin
rabbitmq-plugins enable rabbitmq_management

# Verify: Open http://localhost:15672 (guest/guest)
```

### Step 2: Build All Applications
```bash
cd deployment
chmod +x build-all.sh
./build-all.sh
```

### Step 3: Start Server-v2
```bash
# Terminal 1
cd server-v2
java -jar target/WebSocketServer-1.0-SNAPSHOT.jar

# Should see: "RabbitMQ connection pool initialized"
# Health check: curl http://localhost:8080/health
```

### Step 4: Start Consumer
```bash
# Terminal 2
cd consumer
java -jar target/MessageConsumer-1.0-SNAPSHOT.jar

# Should see: "Starting 20 consumer threads..."
# Metrics: curl http://localhost:8081/metrics
```

### Step 5: Run Client Test
```bash
# Terminal 3
cd client-part1
java -jar target/ChatClient-1.0-SNAPSHOT.jar \
  --server.url=ws://localhost:8080/ws \
  --num.threads=64 \
  --messages.per.thread=1000 \
  --room.count=20
```

### Step 6: Monitor

**RabbitMQ Console:**
- Open http://localhost:15672
- Go to "Queues" tab
- Watch message rates and queue depths

**Consumer Metrics:**
```bash
# Watch metrics in real-time
watch -n 2 'curl -s http://localhost:8081/metrics | jq'
```

## AWS Deployment (Quick)

### 1. Launch Instances
```bash
# Launch 3 EC2 instances (Ubuntu 22.04):
# - 1x t3.medium for RabbitMQ
# - 1x t3.small for server-v2
# - 1x t3.medium for consumer

# Install Java on server and consumer instances:
sudo apt-get update && sudo apt-get install -y openjdk-17-jre-headless
```

### 2. Setup RabbitMQ
```bash
# On RabbitMQ instance
scp -i key.pem deployment/setup-rabbitmq.sh ubuntu@<rabbitmq-ip>:~/
ssh -i key.pem ubuntu@<rabbitmq-ip>
chmod +x setup-rabbitmq.sh && ./setup-rabbitmq.sh
```

### 3. Deploy Server
```bash
# Upload and run
scp -i key.pem server-v2/target/*.jar ubuntu@<server-ip>:~/
scp -i key.pem deployment/deploy-server.sh ubuntu@<server-ip>:~/
ssh -i key.pem ubuntu@<server-ip>
./deploy-server.sh <rabbitmq-private-ip> server-1 8080
```

### 4. Deploy Consumer
```bash
# Upload and run
scp -i key.pem consumer/target/*.jar ubuntu@<consumer-ip>:~/
scp -i key.pem deployment/deploy-consumer.sh ubuntu@<consumer-ip>:~/
ssh -i key.pem ubuntu@<consumer-ip>
./deploy-consumer.sh <rabbitmq-private-ip> 20 8081
```

### 5. Create ALB
```bash
# AWS Console → EC2 → Load Balancers → Create
# Type: Application Load Balancer
# Target: Server instance on port 8080
# Health check: /health
# Enable sticky sessions
```

### 6. Test
```bash
# From local machine
cd client-part1
java -jar target/ChatClient-1.0-SNAPSHOT.jar \
  --server.url=ws://<alb-dns-name>/ws \
  --num.threads=256 \
  --messages.per.thread=2000
```

## Configuration Quick Reference

### Increase Throughput
```bash
# Server-v2: Increase channel pool
--rabbitmq.pool.size=30

# Consumer: More threads
--consumer.thread.count=40

# Consumer: Higher prefetch
--consumer.prefetch.count=15
```

### Reduce Memory Usage
```bash
# Consumer: Fewer threads
--consumer.thread.count=10

# Consumer: Lower prefetch
--consumer.prefetch.count=5

# Server: Smaller pool
--rabbitmq.pool.size=10
```

## Troubleshooting (Common Issues)

### ❌ Server won't start
```bash
# Check RabbitMQ is running
brew services list | grep rabbitmq

# Check port availability
lsof -i :8080

# Check logs
tail -f server.log
```

### ❌ Consumer not consuming
```bash
# Verify queues exist
rabbitmqctl list_queues

# Check consumer logs
tail -f consumer.log

# Check metrics
curl http://localhost:8081/health
```

### ❌ High queue depth
```bash
# Increase consumer threads
# Edit consumer/src/main/resources/application.properties
consumer.thread.count=40

# Rebuild and restart
cd consumer && mvn clean package && java -jar target/*.jar
```

### ❌ Connection refused
```bash
# Check RabbitMQ hostname
ping <rabbitmq-host>

# Check security groups (AWS)
# Ensure port 5672 is open

# Check RabbitMQ service
sudo systemctl status rabbitmq-server
```

## Performance Testing Tips

### Baseline Test (Single Server)
```bash
# 500K messages
java -jar ChatClient.jar \
  --num.threads=256 \
  --messages.per.thread=2000

# Monitor:
# - Client throughput (messages/sec)
# - Queue depth (should plateau)
# - Consumer lag (<100ms)
```

### Load Balanced Test (2 Servers)
```bash
# Same test, point to ALB
# Expected: ~1.5-1.8x throughput improvement
```

### Stress Test (4 Servers)
```bash
# 1M messages
--num.threads=512
--messages.per.thread=2000

# Should handle without message loss
# Queue depth should remain stable
```

## Success Criteria

✅ Server publishes messages to RabbitMQ
✅ Consumer pulls and broadcasts messages
✅ Queue depth remains stable (plateau pattern)
✅ No message loss
✅ Health checks passing
✅ Metrics showing processed messages

## Next Steps

1. Test single server setup locally
2. Tune parameters for optimal throughput
3. Deploy to AWS EC2
4. Configure ALB
5. Run performance tests
6. Collect metrics and screenshots
7. Document results

## Need Help?

Check these files:
- `ASSIGNMENT2_README.md` - Full documentation
- `deployment/AWS_DEPLOYMENT.md` - AWS deployment details
- `IMPLEMENTATION_SUMMARY.md` - What was built
- `assignment2_spec.md` - Assignment requirements

## Tips for Assignment Submission

1. **Screenshots to collect:**
   - Client output showing throughput
   - RabbitMQ management console (queue depths)
   - Consumer metrics endpoint
   - ALB metrics from CloudWatch

2. **Metrics to record:**
   - Total runtime
   - Messages per second
   - Peak queue depth
   - Average queue depth
   - CPU/memory usage

3. **Test scenarios:**
   - Single instance (500K messages)
   - 2 instances with ALB (500K messages)
   - 4 instances with ALB (500K messages)

4. **Document:**
   - Configuration used
   - Instance types
   - Tuning parameters
   - Performance results
   - Queue depth graphs

Good luck! 🚀
