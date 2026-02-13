#!/bin/bash
# EC2 setup script - runs on the EC2 instance
# This script is uploaded and executed by deploy-to-ec2.sh

set -e

echo "========================================="
echo "Setting up ChatFlow Server on EC2"
echo "========================================="

# Check if Java is installed (Java 11+ required, installing Java 17)
if ! command -v java &> /dev/null; then
    echo "☕ Installing Java 17..."
    sudo yum update -y
    sudo yum install -y java-17-amazon-corretto-headless
else
    echo "☕ Java already installed: $(java -version 2>&1 | head -n 1)"
fi

# Install screen for persistent sessions
if ! command -v screen &> /dev/null; then
    echo "📺 Installing screen..."
    sudo yum install -y screen
fi

# Stop existing server if running
echo "🛑 Stopping existing server (if any)..."
screen -S chatflow -X quit 2>/dev/null || true
pkill -f WebSocketServer || true
sleep 2

# Start server in persistent screen session
echo "🚀 Starting ChatFlow server in screen session..."
screen -dmS chatflow bash -c 'java -jar ~/WebSocketServer-1.0-SNAPSHOT.jar 2>&1 | tee chatflow.log'

# Wait for server to start
echo "⏳ Waiting for server to start..."
sleep 3

# Check if server is running
if pgrep -f WebSocketServer > /dev/null; then
    echo "✅ Server is running (PID: $(pgrep -f WebSocketServer))"
    
    # Test health endpoint
    echo "🔍 Testing health endpoint..."
    sleep 2
    if curl -s http://localhost:8080/health | grep -q "healthy"; then
        echo "✅ Health check passed!"
    else
        echo "⚠️  Health check failed (server may still be starting)"
    fi
else
    echo "❌ Server failed to start. Check logs:"
    tail -20 chatflow.log
    exit 1
fi

echo ""
echo "========================================="
echo "✅ Setup Complete!"
echo "========================================="
echo "Server is running on:"
echo "  - Health (REST): http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):8080/health"
echo "  - WebSocket: ws://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):8081/chat/{roomId}"
echo ""
echo "Manage server:"
echo "  View logs: tail -f ~/chatflow.log"
echo "  Attach to screen: screen -r chatflow"
echo "  Detach from screen: Ctrl+A then D"
echo "  Stop server: screen -S chatflow -X quit"
echo "========================================="
