#!/bin/bash

# Deploy Server-v2 Script
# Usage: ./deploy-server.sh <rabbitmq-host> <server-id> [port]

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <rabbitmq-host> <server-id> [port]"
    echo "Example: $0 10.0.1.100 server-1 8080"
    echo "  -> Health on :8080, WebSocket on :8081"
    exit 1
fi

RABBITMQ_HOST=$1
SERVER_ID=$2
PORT=${3:-8080}

echo "Deploying server-v2..."
echo "RabbitMQ Host: $RABBITMQ_HOST"
echo "Server ID: $SERVER_ID"
echo "Port: $PORT"

# Check if JAR exists
if [ ! -f "../server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar" ]; then
    echo "Error: server-v2 JAR not found. Please build first."
    exit 1
fi

# Copy JAR to deployment directory
cp ../server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar ./

# Run server (server-v2 reads configuration from environment variables, not --args)
nohup env \
    HEALTH_PORT=$PORT \
    WEBSOCKET_PORT=$((PORT + 1)) \
    RABBITMQ_HOST=$RABBITMQ_HOST \
    RABBITMQ_PORT=5672 \
    RABBITMQ_USERNAME=admin \
    RABBITMQ_PASSWORD=adminpassword \
    SERVER_ID=$SERVER_ID \
    RABBITMQ_POOL_SIZE=20 \
    ROOM_COUNT=20 \
    java -jar WebSocketServer-1.0-SNAPSHOT.jar \
    > server-$SERVER_ID.log 2>&1 &

PID=$!
echo "Server started with PID: $PID"
echo "Log file: server-$SERVER_ID.log"
echo "Health check:   curl http://localhost:$PORT/health"
echo "WebSocket port: $((PORT + 1))"
