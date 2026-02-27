#!/bin/bash

# Build all applications
echo "Building server-v2..."
cd ../server-v2
mvn clean package -DskipTests

echo "Building consumer..."
cd ../consumer
mvn clean package -DskipTests

echo "Building client-part1..."
cd ../client-part1
mvn clean package -DskipTests

echo "Building client-part2..."
cd ../client-part2
mvn clean package -DskipTests

echo "All applications built successfully!"
echo ""
echo "JAR files location:"
echo "- server-v2/target/WebSocketServer-1.0-SNAPSHOT.jar"
echo "- consumer/target/MessageConsumer-1.0-SNAPSHOT.jar"
echo "- client-part1/target/ChatClient-1.0-SNAPSHOT.jar"
echo "- client-part2/target/ChatClient-1.0-SNAPSHOT.jar"
