# Broadcast Architecture Implementation

## Overview
Implemented a broadcast mechanism where the consumer sends messages back to servers via RabbitMQ, and servers broadcast to their connected WebSocket clients.

## Architecture Flow

```
Client → Server (WebSocket)
         ↓
    Validate & Publish
         ↓
    RabbitMQ (room queues)
         ↓
    Consumer (processes)
         ↓
    Broadcast Queue (new!)
         ↓
    Server (consumes broadcast)
         ↓
    WebSocket → Clients in Room
```

## Key Components

### Server-v2

#### 1. **BroadcastConsumer** (NEW)
- Location: `server-v2/src/main/java/cs6650/assignment1/queue/BroadcastConsumer.java`
- Consumes from `broadcast.queue`
- Receives processed messages from consumer
- Delegates to `ChatWebSocketServer.broadcastToRoom()`

#### 2. **ChatWebSocketServer** (UPDATED)
- Added `roomConnections` map to track WebSocket connections by room
- New method: `broadcastToRoom(QueueMessage)` - broadcasts to all clients in a room
- Updates connection tracking in `onOpen()` and `onClose()`

#### 3. **Main.java** (UPDATED)
- Starts BroadcastConsumer thread on startup
- Manages lifecycle in shutdown hook

### Consumer

#### 1. **BroadcastPublisher** (NEW)
- Location: `consumer/src/main/java/cs6650/assignment1/queue/BroadcastPublisher.java`
- Spring @Component
- Publishes processed messages to `broadcast.queue`
- Used by MessageConsumerService

#### 2. **MessageConsumerService** (UPDATED)
- Now uses `BroadcastPublisher` instead of `SimpMessagingTemplate`
- Publishes to broadcast queue after processing each message
- Servers consume from this queue and broadcast to clients

## Message Flow Details

### 1. Client Sends Message
```
Client → ws://server:8081/chat/5
Message: {"userId": 123, "username": "john", "message": "Hello"}
```

### 2. Server Validates & Publishes
```
Server validates → Creates QueueMessage → Publishes to room.5 queue
Server responds: {"status": "success"} (acknowledgment only)
```

### 3. Consumer Processes
```
Consumer reads from room.5 → Processes → Publishes to broadcast.queue
```

### 4. Server Broadcasts
```
Server reads from broadcast.queue → Broadcasts to all clients in room 5
All clients receive: {"username": "john", "message": "Hello", "status": "broadcast"}
```

## Benefits

1. **Scalability**: Multiple server instances can receive broadcasts
2. **Decoupling**: Servers don't need to know about each other
3. **Load Distribution**: RabbitMQ handles message distribution
4. **Reliability**: At-least-once delivery with acknowledgments
5. **Horizontal Scaling**: Add more servers without code changes

## Configuration

### RabbitMQ Queues
- **Room queues**: `room.1` to `room.20` (existing)
- **Broadcast queue**: `broadcast.queue` (new, auto-created)

### Server-v2 Configuration
No changes needed - uses existing RabbitMQ connection pool

### Consumer Configuration
No changes needed - BroadcastPublisher auto-wired

## Testing

### Start Services
```bash
# Terminal 1: RabbitMQ
brew services start rabbitmq

# Terminal 2: Server
cd server-v2
mvn clean package
java -jar target/WebSocketServer-1.0-SNAPSHOT.jar

# Terminal 3: Consumer
cd consumer
mvn clean package
java -jar target/Consumer-1.0-SNAPSHOT.jar

# Terminal 4: Client
cd client-part2
mvn clean package
java -jar target/ChatClient-1.0-SNAPSHOT.jar
```

### Verify Broadcast
1. Connect multiple clients to same room
2. Send message from one client
3. All clients in room should receive the broadcast
4. Check logs: "Broadcasted message X to room Y: Z successful"

## Monitoring

### RabbitMQ Management Console
```
http://localhost:15672
Username: guest
Password: guest
```

Monitor:
- `broadcast.queue` depth (should stay low)
- Message rates (publish/consume)
- Consumer count on broadcast queue = number of servers

### Server Logs
```
INFO  - BroadcastConsumer starting...
INFO  - Broadcasted message abc123 to room 5: 3 successful, 0 failed
```

### Consumer Logs
```
DEBUG - Sent message abc123 to broadcast queue for room 5
```

## Performance Considerations

1. **Prefetch Count**: BroadcastConsumer uses prefetch=10 for fair distribution
2. **Connection Pooling**: Reuses existing channel pool
3. **Thread Safety**: CopyOnWriteArraySet for room connections
4. **Error Handling**: Failed broadcasts logged, don't block other deliveries

## Future Enhancements

1. Add message persistence for offline users
2. Implement user presence tracking
3. Add typing indicators via broadcast
4. Support direct messages (not just rooms)
5. Add message history retrieval
