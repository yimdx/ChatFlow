# Assignment 2: Message Distribution with RabbitMQ

## Overview

This project implements a distributed chat system using WebSocket servers, RabbitMQ message queues, and consumer services. Messages are published to RabbitMQ by server instances and consumed by dedicated consumer services that broadcast messages to all clients in a room.

## Architecture

```
Client -> ALB -> [Server-v2 Instances] -> RabbitMQ -> [Consumer Instances] -> Clients
```

### Components

1. **server-v2**: WebSocket server that receives messages from clients and publishes to RabbitMQ
2. **consumer**: Message consumer service that pulls messages from RabbitMQ and broadcasts to all clients in a room
3. **RabbitMQ**: Message queue broker using topic exchange for room-based routing

## Part 1: Server-v2 (Message Producer)

### Features

- WebSocket endpoint for client connections
- Message validation and error handling
- RabbitMQ channel pooling for efficient publishing
- Publisher confirms for reliability
- Health check endpoint for ALB

### Queue Configuration

- **Exchange**: `chat.exchange` (topic)
- **Routing Pattern**: `room.{roomId}`
- **Queues**: `room.1` through `room.20`
- **Message TTL**: 24 hours
- **Queue Max Length**: 10,000 messages

### Building and Running

```bash
cd server-v2

# Build
mvn clean package

# Run locally
java -jar target/WebSocketServer-1.0-SNAPSHOT.jar

# Run with custom configuration
java -jar target/WebSocketServer-1.0-SNAPSHOT.jar \
  --server.port=8080 \
  --rabbitmq.host=your-rabbitmq-host \
  --rabbitmq.port=5672 \
  --server.id=server-1
```

### Configuration (application.properties)

```properties
# Server Configuration
server.port=8080
server.id=server-1

# RabbitMQ Configuration
rabbitmq.host=localhost
rabbitmq.port=5672
rabbitmq.username=guest
rabbitmq.password=guest
rabbitmq.pool.size=20

# Chat Configuration
chat.room.count=20
```

## Part 2: Consumer (Message Consumer)

### Features

- Multi-threaded consumer pool (configurable thread count)
- Fair distribution of rooms across consumer threads
- At-least-once delivery guarantee
- Message acknowledgment after successful broadcast
- WebSocket broadcasting to all clients in a room
- Health and metrics endpoints

### Consumer Design

- Configurable number of consumer threads
- Each thread consumes from multiple queues
- Prefetch count for batch processing
- Automatic message acknowledgment
- Failed message handling with nack

### Building and Running

```bash
cd consumer

# Build
mvn clean package

# Run locally
java -jar target/MessageConsumer-1.0-SNAPSHOT.jar

# Run with custom configuration
java -jar target/MessageConsumer-1.0-SNAPSHOT.jar \
  --server.port=8081 \
  --rabbitmq.host=your-rabbitmq-host \
  --consumer.thread.count=20 \
  --consumer.prefetch.count=10
```

### Configuration (application.properties)

```properties
# Server Configuration
server.port=8081

# RabbitMQ Configuration
rabbitmq.host=localhost
rabbitmq.port=5672
rabbitmq.username=guest
rabbitmq.password=guest
rabbitmq.pool.size=40

# Consumer Configuration
consumer.thread.count=20
consumer.prefetch.count=10

# Chat Configuration
chat.room.count=20
```

## RabbitMQ Setup

### Local Setup (macOS)

```bash
# Install RabbitMQ
brew install rabbitmq

# Start RabbitMQ
brew services start rabbitmq

# Enable management plugin
rabbitmq-plugins enable rabbitmq_management

# Access management console
open http://localhost:15672
# Default credentials: guest/guest
```

### EC2 Setup

```bash
# Update packages
sudo apt-get update

# Install Erlang
sudo apt-get install -y erlang

# Install RabbitMQ
sudo apt-get install -y rabbitmq-server

# Start RabbitMQ
sudo systemctl start rabbitmq-server
sudo systemctl enable rabbitmq-server

# Enable management plugin
sudo rabbitmq-plugins enable rabbitmq_management

# Create admin user
sudo rabbitmqctl add_user admin password
sudo rabbitmqctl set_user_tags admin administrator
sudo rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"

# Open ports in security group
# 5672 - AMQP
# 15672 - Management Console
```

## Deployment

### Single Server Deployment

1. Deploy RabbitMQ on EC2 instance
2. Deploy server-v2 on EC2 instance (configure RabbitMQ host)
3. Deploy consumer on EC2 instance (configure RabbitMQ host)
4. Update server-v2 and consumer application.properties with RabbitMQ host

### Load Balanced Deployment (2-4 Servers)

1. Deploy RabbitMQ on separate EC2 instance
2. Deploy multiple server-v2 instances
3. Create Application Load Balancer (ALB)
4. Configure ALB with:
   - Target groups for WebSocket servers
   - Sticky sessions enabled
   - Health check: `/health`
5. Deploy consumer instances
6. All instances should point to the same RabbitMQ instance

### ALB Configuration

```
Protocol: HTTP/HTTPS
Port: 80/443
Target Port: 8080
Health Check Path: /health
Health Check Interval: 30 seconds
Timeout: 5 seconds
Healthy Threshold: 2
Unhealthy Threshold: 3

Sticky Sessions: Enabled
Cookie Duration: 86400 seconds (24 hours)
```

## Message Flow

1. Client connects to WebSocket endpoint via ALB
2. Client sends message to `/app/chat/{roomId}`
3. Server validates and publishes to RabbitMQ exchange `chat.exchange` with routing key `room.{roomId}`
4. Message is routed to queue `room.{roomId}`
5. Consumer thread picks up message from queue
6. Consumer broadcasts message to `/topic/room.{roomId}`
7. All clients subscribed to that topic receive the message
8. Consumer acknowledges message to RabbitMQ

## Queue Message Format

```json
{
  "messageId": "uuid",
  "roomId": "1",
  "userId": "12345",
  "username": "john_doe",
  "message": "Hello everyone!",
  "timestamp": "2026-02-27T10:30:00.000Z",
  "messageType": "TEXT",
  "serverId": "server-1",
  "clientIp": "192.168.1.1"
}
```

## Monitoring

### Server-v2 Health Endpoint

```bash
curl http://localhost:8080/health

Response:
{
  "status": "UP",
  "service": "chat-server"
}
```

### Consumer Metrics Endpoint

```bash
curl http://localhost:8081/metrics

Response:
{
  "messagesProcessed": 50000,
  "messagesFailed": 10,
  "activeRooms": 15,
  "totalUsers": 300,
  "rooms": ["room.1", "room.2", ...]
}
```

### RabbitMQ Management Console

Access at `http://your-rabbitmq-host:15672`

- Monitor queue depths
- View message rates
- Check connection status
- Configure alerts

## Performance Tuning

### Server-v2 Parameters

- `rabbitmq.pool.size`: Channel pool size (default: 20)
- Increase for higher throughput
- Monitor connection count

### Consumer Parameters

- `consumer.thread.count`: Number of consumer threads (default: 20)
- `consumer.prefetch.count`: Messages to prefetch per consumer (default: 10)
- Adjust based on message processing time
- More threads = higher throughput but more resource usage

### Optimal Settings (Recommendations)

For 500K messages test:
- Server channel pool: 20-30
- Consumer threads: 20-40
- Prefetch count: 5-10
- Multiple server instances: 2-4

## Testing

### Build Client Applications

```bash
# Build client-part1 for testing
cd client-part1
mvn clean package

# Run client
java -jar target/ChatClient-1.0-SNAPSHOT.jar \
  --server.url=ws://your-alb-host/ws \
  --num.threads=256 \
  --messages.per.thread=1000
```

### Test Scenarios

1. **Single Server**: 500K messages, monitor throughput
2. **2 Servers with ALB**: 500K messages, check distribution
3. **4 Servers with ALB**: 500K messages, maximum throughput test

### Metrics to Collect

- Client throughput (messages/second)
- Queue depth over time
- Consumer lag
- CPU/Memory usage
- Network I/O

## Troubleshooting

### Issue: Growing Queue Depth

**Solution**: 
- Increase consumer thread count
- Increase prefetch count
- Add more consumer instances

### Issue: Connection Pool Exhaustion

**Solution**:
- Increase `rabbitmq.pool.size`
- Check for channel leaks
- Implement proper channel cleanup

### Issue: High Consumer Lag

**Solution**:
- Optimize message processing
- Increase consumer threads
- Check network latency to RabbitMQ

### Issue: Messages Not Being Delivered

**Solution**:
- Check queue bindings in RabbitMQ
- Verify routing key format
- Check consumer connection status
- Verify clients are subscribed to correct topics

## Project Structure

```
├── server-v2/                 # WebSocket server with RabbitMQ producer
│   ├── src/main/java/
│   │   └── cs6650/assignment1/
│   │       ├── Main.java
│   │       ├── config/
│   │       │   ├── RabbitMQChannelPool.java
│   │       │   ├── RabbitMQSetup.java
│   │       │   └── WebSocketConfig.java
│   │       ├── controller/
│   │       │   ├── ChatController.java
│   │       │   └── HealthController.java
│   │       ├── model/
│   │       │   ├── ChatMessage.java
│   │       │   ├── ChatResponse.java
│   │       │   ├── ErrorResponse.java
│   │       │   └── QueueMessage.java
│   │       └── service/
│   │           └── MessagePublisherService.java
│   └── pom.xml
│
├── consumer/                  # Message consumer service
│   ├── src/main/java/
│   │   └── cs6650/assignment1/
│   │       ├── ConsumerApplication.java
│   │       ├── config/
│   │       │   ├── RabbitMQChannelPool.java
│   │       │   └── WebSocketConfig.java
│   │       ├── controller/
│   │       │   └── HealthController.java
│   │       ├── model/
│   │       │   ├── QueueMessage.java
│   │       │   └── UserInfo.java
│   │       └── service/
│   │           ├── MessageConsumerService.java
│   │           └── RoomManager.java
│   └── pom.xml
│
└── assignment2_spec.md        # Assignment specification
```

## API Endpoints

### Server-v2

- `ws://host:8080/ws` - WebSocket endpoint
- `GET /health` - Health check

### Consumer

- `ws://host:8081/ws` - WebSocket endpoint for broadcasting
- `GET /health` - Health check
- `GET /metrics` - Consumer metrics

## WebSocket Protocol

### Client -> Server

```
CONNECT /ws
SUBSCRIBE /user/queue/reply
SUBSCRIBE /user/queue/errors
SUBSCRIBE /topic/room.{roomId}
SEND /app/chat/{roomId}
```

### Message Format

```json
{
  "userId": 12345,
  "username": "john_doe",
  "message": "Hello!",
  "timestamp": "2026-02-27T10:30:00.000Z",
  "messageType": "TEXT"
}
```

## License

MIT
