#!/bin/bash

# Deploy Consumer Script
# Usage: ./deploy-consumer.sh <rabbitmq-host> [threads] [port]

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <rabbitmq-host> [threads] [port]"
    echo "Example: $0 10.0.1.100 20 8081"
    exit 1
fi

RABBITMQ_HOST=$1
THREADS=${2:-20}
PORT=${3:-8081}

echo "Deploying consumer..."
echo "RabbitMQ Host: $RABBITMQ_HOST"
echo "Consumer Threads: $THREADS"
echo "Port: $PORT"

# Check if JAR exists
if [ ! -f "../consumer/target/MessageConsumer-1.0-SNAPSHOT.jar" ]; then
    echo "Error: consumer JAR not found. Please build first."
    exit 1
fi

# Copy JAR to deployment directory
cp ../consumer/target/MessageConsumer-1.0-SNAPSHOT.jar ./

# Run consumer
nohup java -jar MessageConsumer-1.0-SNAPSHOT.jar \
    --server.port=$PORT \
    --rabbitmq.host=$RABBITMQ_HOST \
    --rabbitmq.port=5672 \
    --rabbitmq.username=admin \
    --rabbitmq.password=adminpassword \
    --consumer.thread.count=$THREADS \
    --consumer.prefetch.count=10 \
    > consumer.log 2>&1 &

PID=$!
echo "Consumer started with PID: $PID"
echo "Log file: consumer.log"
echo "Health check: curl http://localhost:$PORT/health"
echo "Metrics: curl http://localhost:$PORT/metrics"
