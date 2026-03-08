# Deployment Guide - HTTP Broadcast Architecture

## Architecture Overview

```
Client → WebSocket Server → RabbitMQ (room queues)
                                ↓
                           Consumer
                                ↓
                      HTTP POST /broadcast
                                ↓
              All WebSocket Servers (broadcast to clients)
```

## Deployment Steps

### 1. Build All Components

```bash
cd deployment
./build-all.sh
```

This builds:
- `server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar`
- `consumer/target/MessageConsumer-1.0-SNAPSHOT.jar`

### 2. Setup RabbitMQ

```bash
./setup-rabbitmq.sh
```

Or manually with Docker:
```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=adminpassword \
  rabbitmq:3-management
```

### 3. Deploy Servers

**Single Server:**
```bash
./deploy-server.sh <rabbitmq-host> server-1 8080
```

**Multiple Servers:**
```bash
# Server 1 on host1
./deploy-server.sh 10.0.1.100 server-1 8080
# Ports: Health=8080, WebSocket=8081, Broadcast=8082

# Server 2 on host2
./deploy-server.sh 10.0.1.100 server-2 8090
# Ports: Health=8090, WebSocket=8091, Broadcast=8092

# Server 3 on host3
./deploy-server.sh 10.0.1.100 server-3 8100
# Ports: Health=8100, WebSocket=8101, Broadcast=8102
```

### 4. Deploy Consumer

**IMPORTANT**: Consumer needs to know ALL server broadcast endpoints!

**Single Server:**
```bash
./deploy-consumer.sh 10.0.1.100 'http://localhost:8082' 20
```

**Multiple Servers:**
```bash
./deploy-consumer.sh 10.0.1.100 \
  'http://server1-host:8082,http://server2-host:8092,http://server3-host:8102' \
  20
```

**Parameters:**
- Arg 1: RabbitMQ host
- Arg 2: Comma-separated server broadcast URLs (in quotes)
- Arg 3: Consumer thread count (default: 20)

## Port Configuration

Each server uses 3 ports (base + offsets):
- **Base Port**: Health check HTTP endpoint
- **Base + 1**: WebSocket endpoint for clients
- **Base + 2**: Broadcast HTTP endpoint (for consumer)

Example with base port 8080:
- `8080` - Health check: `curl http://server:8080/health`
- `8081` - WebSocket: `ws://server:8081/chat/{roomId}`
- `8082` - Broadcast: `http://server:8082/broadcast` (consumer only)

## AWS EC2 Deployment Example

### Scenario: 3 Servers + 1 Consumer

**Servers:**
```bash
# EC2 Instance 1 (3.235.178.181)
ssh ec2-user@3.235.178.181
cd deployment
./deploy-server.sh 10.0.1.100 server-1 8080

# EC2 Instance 2 (3.235.178.182)
ssh ec2-user@3.235.178.182
cd deployment
./deploy-server.sh 10.0.1.100 server-2 8080

# EC2 Instance 3 (3.235.178.183)
ssh ec2-user@3.235.178.183
cd deployment
./deploy-server.sh 10.0.1.100 server-3 8080
```

**Consumer:**
```bash
# EC2 Instance 4 (3.235.178.184)
ssh ec2-user@3.235.178.184
cd deployment
./deploy-consumer.sh 10.0.1.100 \
  'http://3.235.178.181:8082,http://3.235.178.182:8082,http://3.235.178.183:8082' \
  20
```

### With Private IPs (VPC)

If servers are in same VPC, use private IPs for better performance:
```bash
./deploy-consumer.sh 10.0.1.100 \
  'http://10.0.1.10:8082,http://10.0.1.11:8082,http://10.0.1.12:8082' \
  20
```

## Environment Variables

### Server Environment Variables:
```bash
HEALTH_PORT=8080              # Health check port
WEBSOCKET_PORT=8081           # WebSocket port for clients
BROADCAST_PORT=8082           # HTTP broadcast endpoint (NEW)
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=adminpassword
SERVER_ID=server-1
RABBITMQ_POOL_SIZE=20
ROOM_COUNT=20
```

### Consumer Environment Variables:
```bash
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=adminpassword
CONSUMER_THREAD_COUNT=20
ROOM_COUNT=20
SERVER_URLS=http://server1:8082,http://server2:8082  # NEW - Critical!
```

## Testing

### 1. Health Checks
```bash
# Check each server
curl http://server1:8080/health
curl http://server2:8090/health
curl http://server3:8100/health
```

### 2. WebSocket Connection
```bash
# Install wscat if needed
npm install -g wscat

# Connect to room 1 on server 1
wscat -c ws://server1:8081/chat/1
```

### 3. Send Message
```json
{"userId":"user1","username":"Alice","message":"Hello from server1!"}
```

### 4. Check Logs
```bash
# Server logs
tail -f deployment/server-server-1.log

# Consumer logs
tail -f deployment/consumer.log
```

## Scaling

### Adding More Servers

1. Deploy new server:
```bash
./deploy-server.sh 10.0.1.100 server-4 8110
```

2. **Update consumer** with new server URL:
```bash
# Stop consumer
pkill -f MessageConsumer-1.0-SNAPSHOT.jar

# Redeploy with updated SERVER_URLS
./deploy-consumer.sh 10.0.1.100 \
  'http://server1:8082,http://server2:8092,http://server3:8102,http://server4:8112' \
  20
```

### Adding More Consumers

You can run multiple consumer instances, each will process different messages:
```bash
# Consumer instance 1 (10 threads)
./deploy-consumer.sh 10.0.1.100 'http://server1:8082,http://server2:8092' 10

# Consumer instance 2 (10 threads)
./deploy-consumer.sh 10.0.1.100 'http://server1:8082,http://server2:8092' 10
```

## Troubleshooting

### Consumer can't reach servers
- Check server broadcast port (8082) is accessible
- Verify SERVER_URLS uses correct hostnames/IPs
- Check firewalls allow port 8082

### Messages not broadcasting
- Check consumer logs: `tail -f consumer.log`
- Verify consumer has correct SERVER_URLS
- Test broadcast endpoint: `curl -X POST http://server:8082/broadcast -d '{}'`

### High latency
- Use private IPs in VPC instead of public IPs
- Increase consumer thread count
- Add more consumer instances

## Monitoring

### Check Consumer Stats
Consumer logs statistics every 30 seconds:
```
Messages processed: 1234
Active rooms: 15
Active users: 50
```

### Check RabbitMQ
```bash
# Web UI
http://rabbitmq-host:15672
# Username: admin, Password: adminpassword

# Check queue depths
curl -u admin:adminpassword http://rabbitmq-host:15672/api/queues
```

## Security Notes

1. **Broadcast endpoint** should only be accessible to consumer
   - Use security groups/firewalls to restrict port 8082
   - Don't expose broadcast port publicly

2. **Change default credentials** in production:
   ```bash
   RABBITMQ_USERNAME=your_user
   RABBITMQ_PASSWORD=strong_password
   ```

3. **Use TLS** for production:
   - RabbitMQ with TLS (port 5671)
   - WebSocket with WSS
   - HTTPS for broadcast endpoint
