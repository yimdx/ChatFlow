# HTTP Broadcast Architecture

## Problem Solved

**Original Issue**: Consumer was publishing messages back to RabbitMQ (fanout exchange) for servers to consume. This created:
- Channel pool contention between publishing and consuming
- Risk of channel exhaustion/deadlock
- Extra overhead of double-queuing messages

**Solution**: HTTP callback architecture where consumer makes HTTP POST requests directly to servers.

## Architecture Flow

```
Client → WebSocket → Server → RabbitMQ (room queues)
                                    ↓
                              Consumer (processes)
                                    ↓
                          HTTP POST /broadcast
                                    ↓
                        All Servers (WebSocket broadcast)
```

### Detailed Flow

1. **Client sends message via WebSocket**
   - Client connects to `ws://server:8081/chat/{roomId}`
   - Sends chat message

2. **Server publishes to RabbitMQ**
   - Server receives message from WebSocket client
   - Publishes to `chat.exchange` with routing key `room.{roomId}`
   - Message goes to corresponding room queue

3. **Consumer processes from queue**
   - Consumer thread reads from assigned room queues
   - Processes message (logging, analytics, etc.)
   - **No publishing back to RabbitMQ**

4. **Consumer broadcasts via HTTP**
   - Consumer makes HTTP POST to all registered servers
   - Endpoint: `http://server:8082/broadcast`
   - Payload: JSON serialized `QueueMessage`

5. **Server broadcasts to WebSocket clients**
   - Server receives HTTP POST at `/broadcast` endpoint
   - Deserializes `QueueMessage`
   - Broadcasts to all WebSocket clients in that room

## Components

### Server-v2

**Ports:**
- `8080`: Health check HTTP server (`/health`)
- `8081`: WebSocket server (`/chat/{roomId}`)
- `8082`: Broadcast HTTP server (`/broadcast`) - **NEW**

**Key Changes:**
1. Added HTTP broadcast server using `com.sun.net.httpserver.HttpServer`
2. Removed `BroadcastConsumer` (no longer consuming from fanout exchange)
3. POST `/broadcast` endpoint receives messages from consumer
4. Broadcasts received messages to WebSocket clients via `webSocketServer.broadcastToRoom()`

**Configuration:**
```bash
HEALTH_PORT=8080
WEBSOCKET_PORT=8081
BROADCAST_PORT=8082    # NEW
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
SERVER_ID=server-1
```

### Consumer

**Key Changes:**
1. Added `java.net.http.HttpClient` for HTTP requests
2. Removed `BroadcastPublisher` (no longer publishing to fanout exchange)
3. Added `broadcastToServers()` method with synchronous HTTP POST
4. Broadcasts to all servers before ACK-ing message (ensures delivery)

**Configuration:**
```bash
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
CONSUMER_THREAD_COUNT=20
ROOM_COUNT=20
SERVER_URLS=http://server1:8082,http://server2:8082,http://server3:8082    # NEW
```

## Benefits

1. **No Channel Contention**
   - Server only publishes (channel pool for outbound)
   - Consumer only consumes (channel pool for inbound)
   - No shared channel pool between pub/sub operations

2. **Simpler RabbitMQ Topology**
   - No fanout exchange needed
   - Only room queues (1-20)
   - Cleaner queue management

3. **Direct Server Communication**
   - Consumer → Server via HTTP (no intermediate queue)
   - Faster message delivery
   - Synchronous confirmation (200 OK)

4. **Better Error Handling**
   - HTTP status codes for immediate feedback
   - Can retry failed servers
   - No need for DLQ (dead letter queue) for broadcast failures

5. **Scalability**
   - Easy to add/remove servers (just update SERVER_URLS)
   - No need to manage fanout queue bindings
   - HTTP load balancing possible

## Trade-offs

**Pros:**
- No channel pool contention
- Direct communication path
- Simple configuration (comma-separated URLs)
- HTTP is well-understood and debuggable

**Cons:**
- Consumer needs to know all server URLs (service discovery needed for dynamic scaling)
- HTTP overhead vs AMQP (but minimal for JSON payloads)
- Synchronous calls may slow consumer (mitigated with async HTTP client if needed)

## Testing

### Start Server
```bash
cd server-v2
export BROADCAST_PORT=8082
mvn clean package
java -jar target/server-v2-1.0-SNAPSHOT.jar
```

### Start Consumer
```bash
cd consumer
export SERVER_URLS=http://localhost:8082
mvn clean package
java -jar target/consumer-1.0-SNAPSHOT.jar
```

### Send Test Message
```bash
# Connect WebSocket client to room 1
wscat -c ws://localhost:8081/chat/1

# Send message
{"userId":"user1","username":"Alice","message":"Hello!"}

# Consumer will:
# 1. Read from room.1 queue
# 2. HTTP POST to http://localhost:8082/broadcast
# 3. Server broadcasts to all WebSocket clients in room 1
```

## Multi-Server Deployment

For multiple servers:

```bash
# Server 1
export SERVER_ID=server-1
export BROADCAST_PORT=8082
java -jar server-v2-1.0-SNAPSHOT.jar

# Server 2
export SERVER_ID=server-2
export WEBSOCKET_PORT=8091
export BROADCAST_PORT=8092
java -jar server-v2-1.0-SNAPSHOT.jar

# Consumer
export SERVER_URLS=http://server1:8082,http://server2:8092
java -jar consumer-1.0-SNAPSHOT.jar
```

Consumer will broadcast to ALL servers, ensuring all connected clients receive messages.

## Future Enhancements

1. **Service Discovery**: Replace static SERVER_URLS with dynamic discovery (Consul, Eureka, etc.)
2. **Load Balancing**: Add reverse proxy for server pool
3. **Async HTTP**: Use async HTTP client for non-blocking broadcasts
4. **Circuit Breaker**: Add resilience patterns for server failures
5. **Metrics**: Track broadcast success/failure rates per server
