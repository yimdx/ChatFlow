# AWS EC2 Deployment Guide

## Prerequisites

- AWS Account
- Key pair for EC2 access
- Security groups configured for:
  - WebSocket servers: Port 8080 (health check HTTP) and Port 8081 (WebSocket)
  - Consumer: Port 8080 (health/metrics HTTP)
  - RabbitMQ: Ports 5672 (AMQP), 15672 (Management Console)
  - ALB: Ports 80, 443

## Instance Types

Recommended instance types for testing:

- **RabbitMQ**: t3.medium (2 vCPU, 4 GB RAM)
- **Server instances**: t3.small (2 vCPU, 2 GB RAM)
- **Consumer instances**: t3.medium (2 vCPU, 4 GB RAM)

## Step 1: Deploy RabbitMQ

```bash
# Launch EC2 instance (Ubuntu 22.04)
# SSH into instance
ssh -i your-key.pem ubuntu@<rabbitmq-ec2-ip>

# Upload setup script
scp -i your-key.pem setup-rabbitmq.sh ubuntu@<rabbitmq-ec2-ip>:~/

# Run setup
chmod +x setup-rabbitmq.sh
./setup-rabbitmq.sh

# Verify RabbitMQ is running
sudo systemctl status rabbitmq-server

# Access management console
# Open http://<rabbitmq-ec2-public-ip>:a
# Login with admin/adminpassword
```

## Step 2: Build Applications Locally

```bash
# On your local machine
cd deployment
chmod +x build-all.sh
./build-all.sh
```

## Step 3: Deploy Server Instances

For each server instance:

```bash
# Launch EC2 instance (Ubuntu 22.04)
# Install Java 17

# co 3.236.26.204
# mq 98.80.127.228
# s1 3.235.178.181

scp WebSocketServer-1.0-SNAPSHOT.jar ec2-user@3.235.178.181

./deploy-server.sh 98.80.127.228 server-1

./deploy-consumer.sh 98.80.127.228 3.235.178.181 20 8081


ssh -i your-key.pem ubuntu@<server-ec2-ip>
sudo apt-get update
sudo apt-get install -y openjdk-17-jre-headless

# Upload JAR
scp -i your-key.pem ../server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar ubuntu@<server-ec2-ip>:~/

# Upload deployment script
scp -i your-key.pem deploy-server.sh ubuntu@<server-ec2-ip>:~/

# Run server
chmod +x deploy-server.sh
./deploy-server.sh <rabbitmq-private-ip> server-1 8080
# Health check server starts on :8080, WebSocket on :8081

# Verify health
curl http://localhost:8080/health
# Verify WebSocket port is listening
ss -tlnp | grep 8081
```

## Step 4: Deploy Consumer Instance

```bash
# Launch EC2 instance (Ubuntu 22.04)
# Install Java 17
ssh -i your-key.pem ubuntu@<consumer-ec2-ip>
sudo apt-get update
sudo apt-get install -y openjdk-17-jre-headless

# Upload JAR
scp -i your-key.pem ../consumer/target/MessageConsumer-1.0-SNAPSHOT.jar ubuntu@<consumer-ec2-ip>:~/

# Upload deployment script
scp -i your-key.pem deploy-consumer.sh ubuntu@<consumer-ec2-ip>:~/

# Run consumer
chmod +x deploy-consumer.sh
./deploy-consumer.sh <rabbitmq-private-ip> 20 8081

# Verify
curl http://localhost:8081/health
curl http://localhost:8081/metrics
```

## Step 5: Configure Application Load Balancer (ALB)

### Create Target Group

1. Go to EC2 → Target Groups → Create target group
2. Configuration:
   - Target type: Instances
   - Protocol: HTTP
   - Port: **8081** (WebSocket port — this is where clients connect)
   - VPC: Select your VPC
   - Health check:
     - Protocol: HTTP
     - Path: `/health`
     - **Port: Override → 8080** (health check HTTP server is on a separate port)
     - Interval: 30 seconds
     - Timeout: 5 seconds
     - Healthy threshold: 2
     - Unhealthy threshold: 3

3. Register server instances as targets

### Create Application Load Balancer

1. Go to EC2 → Load Balancers → Create load balancer
2. Choose Application Load Balancer
3. Configuration:
   - Name: chat-server-alb
   - Scheme: Internet-facing
   - IP address type: IPv4
   - Network: Select your VPC and subnets (at least 2 AZs)
   - Security groups: Allow inbound HTTP/HTTPS
   - Listener:
     - Protocol: HTTP
     - Port: 80
     - Forward to: Select target group created above

4. Enable Sticky Sessions:
   - Go to Target Group → Attributes
   - Enable Stickiness
   - Stickiness type: Load balancer generated cookie
   - Stickiness duration: 86400 seconds (24 hours)

5. Configure Connection Settings:
   - Idle timeout: 120 seconds (for WebSocket connections)

## Step 6: Testing

### Test Single Server

```bash
# From your local machine
cd client-part2

# Run test (ALB DNS routes WebSocket to :8081 on servers)
java -jar target/ChatClient-1.0-SNAPSHOT.jar \
  --server.url=ws://<alb-dns-name>/chat/5 \
  --num.threads=128 \
  --messages.per.thread=2000 \
  --room.count=20
```

### Monitor During Test

1. **RabbitMQ Management Console**:
   - http://<rabbitmq-public-ip>:15672
   - Watch queue depths
   - Monitor message rates

2. **Consumer Metrics**:
   ```bash
   watch -n 5 'curl -s http://<consumer-public-ip>:8081/metrics'
   ```

3. **CloudWatch**:
   - ALB request count
   - Target health
   - Response times

### Test with Multiple Servers

Repeat tests with 2 and 4 server instances registered in the ALB target group.

## Scaling Up

### Add More Server Instances

1. Launch new EC2 instances
2. Deploy server-v2 using deploy-server.sh
3. Register instances in ALB target group
4. Wait for health checks to pass

### Add More Consumer Instances

1. Launch new EC2 instances
2. Deploy consumer using deploy-consumer.sh
3. Consumers will automatically distribute queue consumption

## Monitoring and Troubleshooting

### Check Server Logs

```bash
ssh -i your-key.pem ubuntu@<server-ec2-ip>
tail -f server-server-1.log
```

### Check Consumer Logs

```bash
ssh -i your-key.pem ubuntu@<consumer-ec2-ip>
tail -f consumer.log
```

### Check RabbitMQ Queues

```bash
ssh -i your-key.pem ubuntu@<rabbitmq-ec2-ip>
sudo rabbitmqctl list_queues name messages consumers
```

### Common Issues

**Issue**: High queue depth
- Solution: Increase consumer threads or add more consumer instances

**Issue**: Server not publishing
- Solution: Check RabbitMQ connectivity, verify security group rules

**Issue**: Messages not being delivered to clients
- Solution: Check consumer logs, verify WebSocket connections

## Cleanup

```bash
# Stop services
ssh ubuntu@<instance-ip> 'pkill -f WebSocketServer'
ssh ubuntu@<instance-ip> 'pkill -f MessageConsumer'

# Terminate EC2 instances
# Delete ALB and target groups
# Delete RabbitMQ instance
```

## Cost Estimation

For a 2-hour testing session:
- 1x t3.medium (RabbitMQ): ~$0.08
- 4x t3.small (Servers): ~$0.16
- 1x t3.medium (Consumer): ~$0.08
- 1x ALB: ~$0.04
- **Total**: ~$0.36/hour

## Security Best Practices

1. Use private subnets for RabbitMQ and consumer
2. Use VPC security groups to restrict access
3. Change default RabbitMQ admin password
4. Use HTTPS for ALB in production
5. Enable CloudWatch logging
6. Use IAM roles instead of access keys

## Performance Tips

1. Start with 2 server instances and 1 consumer
2. Monitor queue depth during tests
3. Tune consumer thread count based on CPU usage
4. Increase prefetch count if processing is fast
5. Use CloudWatch to identify bottlenecks
