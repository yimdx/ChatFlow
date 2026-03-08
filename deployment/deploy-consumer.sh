#!/bin/bash

# Deploy Consumer Script
# Usage: ./deploy-consumer.sh <rabbitmq-host> <server-urls> [threads]

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <rabbitmq-host> <server-urls> [threads]"
    echo "Example: $0 10.0.1.100 'http://server1:8082,http://server2:8082' 20"
    echo "  server-urls: comma-separated list of server broadcast endpoints"
    exit 1
fi

RABBITMQ_HOST=$1
SERVER_URLS=$2
THREADS=${3:-20}

echo "Deploying consumer..."
echo "RabbitMQ Host: $RABBITMQ_HOST"
echo "Server URLs: $SERVER_URLS"
echo "Consumer Threads: $THREADS"

# Check if JAR exists
if [ ! -f "../consumer/target/MessageConsumer-1.0-SNAPSHOT.jar" ]; then
    echo "Error: consumer JAR not found. Please build first."
    exit 1
fi

# Copy JAR to deployment directory
cp ../consumer/target/MessageConsumer-1.0-SNAPSHOT.jar ./

# Run consumer (uses environment variables)
nohup env \
    RABBITMQ_HOST=$RABBITMQ_HOST \
    RABBITMQ_PORT=5672 \
    RABBITMQ_USERNAME=admin \
    RABBITMQ_PASSWORD=adminpassword \
    CONSUMER_THREAD_COUNT=$THREADS \
    ROOM_COUNT=20 \
    SERVER_URLS="$SERVER_URLS" \
    java -jar MessageConsumer-1.0-SNAPSHOT.jar \
    > consumer.log 2>&1 &

PID=$!
echo "Consumer started with PID: $PID"
echo "Log file: consumer.log"
echo "Broadcasting to: $SERVER_URLS"
