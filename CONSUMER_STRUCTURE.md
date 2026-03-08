# Consumer Project Structure (No Spring Boot, No Lombok)

## Overview
The consumer service has been reorganized as a standalone Java application without Spring Boot or Lombok dependencies.

## Project Structure

```
consumer/
├── pom.xml                                    # Maven configuration (no Spring Boot parent)
└── src/main/java/cs6650/assignment1/
    ├── consumer/
    │   ├── Main.java                         # Standalone main entry point
    │   ├── MessageConsumerThread.java        # RabbitMQ consumer thread
    │   ├── RoomManager.java                  # Room/user state manager
    │   └── queue/
    │       └── RabbitMQConnection.java       # RabbitMQ connection manager
    ├── model/
    │   ├── QueueMessage.java                 # Message model (with manual getters/setters)
    │   └── UserInfo.java                     # User info model (with manual getters/setters)
    └── queue/
        └── BroadcastPublisher.java           # Fanout exchange publisher
```

## Key Changes

### 1. Removed Spring Boot
- **Removed** Spring Boot parent POM
- **Removed** Spring Boot dependencies (web, websocket)
- **Removed** Spring Boot application class (ConsumerApplication.java)
- **Removed** Spring configuration classes (config/)
- **Removed** Spring controllers (controller/)
- **Removed** Spring services (service/)

### 2. Removed Lombok
- **Removed** Lombok dependency
- **Added** manual getters/setters to all model classes:
  - QueueMessage.java
  - UserInfo.java

### 3. Dependencies (pom.xml)

```xml
<dependencies>
    <!-- RabbitMQ Client -->
    <dependency>
        <groupId>com.rabbitmq</groupId>
        <artifactId>amqp-client</artifactId>
        <version>5.20.0</version>
    </dependency>

    <!-- JSON processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.16.0</version>
    </dependency>

    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
        <version>2.16.0</version>
    </dependency>

    <!-- Logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.9</version>
    </dependency>

    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.4.14</version>
    </dependency>
</dependencies>
```

## Main Entry Point

**File:** `cs6650.assignment1.consumer.Main`

### Features:
- Standalone Java application (no Spring Boot)
- Environment variable configuration:
  - `RABBITMQ_HOST` (default: localhost)
  - `RABBITMQ_PORT` (default: 5672)
  - `RABBITMQ_USERNAME` (default: guest)
  - `RABBITMQ_PASSWORD` (default: guest)
  - `CONSUMER_THREAD_COUNT` (default: 20)
  - `ROOM_COUNT` (default: 20)
- Multi-threaded consumer with room distribution
- Graceful shutdown hook

## Building

```bash
# Build consumer
cd consumer
mvn clean package -DskipTests

# Or build all projects
cd deployment
./build-all.sh
```

## Running

```bash
# Run consumer
java -jar target/MessageConsumer-1.0-SNAPSHOT.jar

# With environment variables
RABBITMQ_HOST=rabbitmq.example.com \
CONSUMER_THREAD_COUNT=40 \
java -jar target/MessageConsumer-1.0-SNAPSHOT.jar
```

## Architecture

### Message Flow:
1. **RabbitMQ Queues** → Messages arrive in room-specific queues (room.1 to room.20)
2. **MessageConsumerThread** → Consumer threads process messages from assigned rooms
3. **BroadcastPublisher** → Publishes processed messages to fanout exchange
4. **Server Instances** → All server instances receive broadcasts and send to WebSocket clients

### Components:

#### Main.java
- Application entry point
- Creates RabbitMQ connection
- Spawns consumer threads
- Distributes rooms across threads
- Handles shutdown

#### MessageConsumerThread.java
- Consumes messages from assigned room queues
- Processes messages (JSON parsing)
- Publishes to broadcast exchange
- Thread-safe operation

#### RoomManager.java
- Tracks active rooms and users
- Thread-safe using ConcurrentHashMap
- Provides room/user statistics

#### BroadcastPublisher.java
- Publishes to fanout exchange ("broadcast.fanout")
- All server instances receive all messages
- Thread-safe channel management

## Model Classes

### QueueMessage
- Contains: messageId, roomId, userId, username, message, timestamp, messageType, serverId, clientIp
- Jackson annotations for JSON serialization
- Manual getters/setters (no Lombok)

### UserInfo
- Contains: userId, username, roomId, sessionId, lastSeen
- Manual getters/setters (no Lombok)

## Notes

- **No Spring Boot**: Pure Java application with explicit dependency injection
- **No Lombok**: All getters/setters manually written for clarity
- **Thread-safe**: Uses concurrent data structures (ConcurrentHashMap, CopyOnWriteArraySet)
- **Scalable**: Multi-threaded consumer design with configurable thread count
- **Reliable**: Graceful shutdown with proper resource cleanup
