# ChatFlow Client - Part 1

## Overview

This is the basic multithreaded WebSocket client for ChatFlow that simulates high-volume chat traffic by sending 500,000 messages to the server.

## Features

- **Warmup Phase**: 32 threads sending 1,000 messages each (32,000 total)
- **Main Phase**: Optimized thread pool sending remaining 468,000 messages
- **Message Generator**: Dedicated thread generating messages to prevent blocking
- **Connection Pooling**: Persistent WebSocket connections for efficiency
- **Retry Logic**: Exponential backoff with up to 5 retries per message
- **Performance Metrics**: Tracks throughput, success/failure rates, and connection statistics

## Architecture

### Components

1. **Main.java**: Orchestrates the client execution and manages phases
2. **MessageGenerator**: Single thread generating all 500K messages
3. **MessageSender**: Worker thread that sends messages via WebSocket
4. **ChatWebSocketClient**: WebSocket client implementation with retry logic
5. **ChatMessage**: Message model matching server schema
6. **ChatResponse**: Response model from server

### Threading Model

- **Warmup Phase**: 32 fixed threads
- **Main Phase**: Dynamic thread pool (typically CPU cores * 4)
- **Message Generator**: 1 dedicated thread
- **Message Queue**: Thread-safe blocking queue for message distribution

## Configuration

Edit the `SERVER_URL` constant in [Main.java](src/main/java/cs6650/assignment1/Main.java):

```java
private static final String SERVER_URL = "ws://localhost:8080";
```

For AWS deployment, change to:
```java
private static final String SERVER_URL = "ws://your-ec2-instance.compute.amazonaws.com:8080";
```

## Building

```bash
cd client-part1
mvn clean package
```

## Running

```bash
mvn exec:java -Dexec.mainClass="cs6650.assignment1.Main"
```

Or run the compiled JAR:

```bash
java -jar target/client-part1-1.0-SNAPSHOT.jar
```

## Expected Output

```
========================================
ChatFlow Client - Part 1
========================================
Server URL: ws://localhost:8080
Total messages to send: 500000
Warmup threads: 32
Warmup messages per thread: 1000
========================================
Starting Warmup Phase...
Warmup Phase completed in 12345 ms
Warmup throughput: 2593.2 messages/second
Starting Main Phase...
Main phase using 16 threads
Messages per thread: 29250
Main Phase completed in 45678 ms
========================================
PERFORMANCE RESULTS
========================================
1. Successful messages sent: 500000
2. Failed messages: 0
3. Total runtime: 58023 ms (58.023 seconds)
   - Warmup phase: 12345 ms
   - Main phase: 45678 ms
4. Overall throughput: 8619.5 messages/second
   - Warmup throughput: 2593.2 messages/second
   - Main phase throughput: 10248.7 messages/second
5. Connection statistics:
   - Total connections: 48
   - Reconnections: 0
========================================
```

## Message Generation

The client generates random messages with the following distribution:

- **userId**: Random between 1-100,000
- **username**: Generated as "user{userId}"
- **message**: Random from 50 predefined messages
- **roomId**: Random between 1-20
- **messageType**: 90% TEXT, 5% JOIN, 5% LEAVE
- **timestamp**: Current time in ISO-8601 format

## Performance Tuning

### JVM Options

For better performance, run with:

```bash
java -Xms2g -Xmx4g -jar target/client-part1-1.0-SNAPSHOT.jar
```

### Thread Pool Tuning

Adjust the thread pool size in [Main.java](src/main/java/cs6650/assignment1/Main.java):

```java
int optimalThreads = Runtime.getRuntime().availableProcessors() * 4;
```

Try different multipliers (2, 4, 8) to find the optimal configuration for your hardware.

## Dependencies

- **Java-WebSocket 1.5.4**: WebSocket client library
- **Jackson 2.15.2**: JSON serialization
- **SLF4J/Logback**: Logging framework

## Troubleshooting

### Connection Refused
- Ensure the server is running
- Check the SERVER_URL is correct
- Verify firewall rules allow WebSocket connections

### Low Throughput
- Increase thread pool size
- Check network latency
- Verify server has enough resources
- Review server logs for bottlenecks

### Out of Memory
- Reduce message queue size
- Increase JVM heap: `-Xmx4g`
- Reduce thread count

## Little's Law Analysis

Before running, measure:
1. Single message round-trip time (RTT)
2. Server processing capacity

Expected throughput = (Number of connections × 1000) / RTT_ms

Compare actual results with theoretical predictions.
