# ChatFlow WebSocket Server

## Overview

A lightweight WebSocket server built with Java-WebSocket library that handles real-time chat messages with validation and room-based connections.

## Features

- ✅ WebSocket endpoint: `ws://host:port/chat/{roomId}`
- ✅ Room-based connections (1-20 rooms)
- ✅ Message validation (userId, username, message, timestamp, messageType)
- ✅ JSON message format
- ✅ Error handling with detailed responses
- ✅ Lightweight (no Spring Boot overhead)
- ✅ Easy deployment

## Requirements

- Java 17 or higher
- Maven 3.6+

## Building

```bash
cd server
mvn clean package
```

This creates an executable JAR: `target/WebSocketServer-1.0-SNAPSHOT.jar`

## Running

### Default port (8080):
```bash
java -jar target/WebSocketServer-1.0-SNAPSHOT.jar
```

### Custom port:
```bash
java -jar target/WebSocketServer-1.0-SNAPSHOT.jar 9090
```

### Using Maven:
```bash
mvn exec:java -Dexec.mainClass="cs6650.assignment1.Main"
```

## WebSocket Endpoint

**Endpoint:** `ws://localhost:8080/chat/{roomId}`

Where `{roomId}` is a number between 1-20.

### Example Connection:
```javascript
const ws = new WebSocket('ws://localhost:8080/chat/5');
```

## Message Format

### Request (Client → Server):

```json
{
  "userId": 12345,
  "username": "user12345",
  "message": "Hello everyone!",
  "timestamp": "2026-02-13T10:30:00.000Z",
  "messageType": "TEXT"
}
```

**Validation Rules:**
- `userId`: Integer, 1-100000
- `username`: String, 3-20 alphanumeric characters
- `message`: String, 1-500 characters
- `timestamp`: ISO-8601 format
- `messageType`: TEXT | JOIN | LEAVE

### Success Response (Server → Client):

```json
{
  "userId": 12345,
  "username": "user12345",
  "message": "Hello everyone!",
  "clientTimestamp": "2026-02-13T10:30:00.000Z",
  "serverTimestamp": "2026-02-13T10:30:00.123Z",
  "messageType": "TEXT",
  "status": "success"
}
```

### Error Response (Server → Client):

```json
{
  "status": "error",
  "timestamp": "2026-02-13T10:30:00.123Z",
  "errors": [
    "userId must be between 1 and 100000",
    "username must be 3-20 characters"
  ]
}
```

## Architecture

```
Client
  ↓
WebSocket Connection (/chat/{roomId})
  ↓
ChatWebSocketServer
  ↓
MessageValidator
  ↓
Response (ChatResponse or ErrorResponse)
  ↓
Client
```

### Key Components:

1. **Main.java** - Entry point, starts the server
2. **ChatWebSocketServer.java** - WebSocket server implementation
3. **MessageValidator.java** - Validates incoming messages
4. **ChatMessage.java** - Message model
5. **ChatResponse.java** - Success response model
6. **ErrorResponse.java** - Error response model

## Testing

### Using wscat:
```bash
npm install -g wscat
wscat -c ws://localhost:8080/chat/1

# Then send a message:
{"userId":123,"username":"testuser","message":"Hello!","timestamp":"2026-02-13T10:00:00.000Z","messageType":"TEXT"}
```

### Using your client:
```bash
cd client-part1
mvn exec:java -Dexec.mainClass="cs6650.assignment1.Main"
```

## Configuration

### Port Configuration
Change the port by passing it as a command-line argument:
```bash
java -jar target/WebSocketServer-1.0-SNAPSHOT.jar 9090
```

### Logging
Edit `src/main/resources/logback.xml` to configure logging levels and output.

Logs are written to:
- Console (STDOUT)
- File: `logs/chatflow-server.log`

## AWS EC2 Deployment

1. **Launch EC2 instance** (t2.micro, Amazon Linux 2)

2. **Install Java 17:**
```bash
sudo yum install java-17-amazon-corretto -y
```

3. **Upload JAR file:**
```bash
scp -i your-key.pem target/WebSocketServer-1.0-SNAPSHOT.jar ec2-user@your-instance:/home/ec2-user/
```

4. **Configure Security Group:**
   - Inbound rule: Custom TCP, Port 8080, Source: 0.0.0.0/0
   - Inbound rule: SSH, Port 22, Source: Your IP

5. **Run server:**
```bash
ssh -i your-key.pem ec2-user@your-instance
java -jar WebSocketServer-1.0-SNAPSHOT.jar
```

6. **Run in background (optional):**
```bash
nohup java -jar WebSocketServer-1.0-SNAPSHOT.jar > server.log 2>&1 &
```

7. **Test connection:**
```bash
wscat -c ws://your-ec2-public-ip:8080/chat/1
```

## Performance Tuning

### JVM Options
```bash
java -Xms1g -Xmx2g -XX:+UseG1GC -jar target/WebSocketServer-1.0-SNAPSHOT.jar
```

### Connection Limits
The server can handle thousands of concurrent connections. Monitor with:
```bash
# Check server status
jps -l
jstat -gc <pid>
```

## Monitoring

### Active Connections
The server logs connection events:
- `Client connected to room X`
- `Connection closed for room X`

### Error Tracking
All validation and processing errors are logged with room context.

## Troubleshooting

### Port already in use
```bash
# Find process using port 8080
lsof -i :8080
# Kill it
kill -9 <PID>
```

### Connection refused
- Check firewall rules
- Verify server is running: `jps -l`
- Check logs: `tail -f logs/chatflow-server.log`

### High memory usage
- Reduce JVM heap size
- Monitor with: `jstat -gcutil <pid> 1000`

## Dependencies

- **Java-WebSocket 1.5.4** - WebSocket server library
- **Jackson 2.15.2** - JSON processing
- **SLF4J/Logback** - Logging

## License

CS6650 Assignment 1 - Educational purposes only
