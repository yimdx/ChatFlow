#!/bin/bash
# Deploy ChatFlow server to EC2
# Usage: ./deploy-to-ec2.sh <EC2_USER@EC2_HOST> <PEM_KEY_PATH>
# Example: ./deploy-to-ec2.sh ec2-user@54.123.45.67 ~/.ssh/my-key.pem

set -e

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <EC2_USER@EC2_HOST> <PEM_KEY_PATH>"
    echo "Example: $0 ec2-user@54.123.45.67 ~/.ssh/my-key.pem"
    exit 1
fi

EC2_HOST=$1
PEM_KEY=$2

echo "========================================="
echo "Deploying ChatFlow Server to EC2"
echo "========================================="
echo "Target: $EC2_HOST"
echo "========================================="

# Upload JAR file
echo "📦 Uploading server JAR..."
scp -i "$PEM_KEY" target/WebSocketServer-1.0-SNAPSHOT.jar "$EC2_HOST:~/"

# Upload and run setup script
echo "📤 Uploading setup script..."
scp -i "$PEM_KEY" ec2-setup.sh "$EC2_HOST:~/"

echo "🚀 Running setup on EC2..."
ssh -i "$PEM_KEY" "$EC2_HOST" 'bash ~/ec2-setup.sh'

echo ""
echo "========================================="
echo "✅ Deployment Complete!"
echo "========================================="
echo "Health endpoint: http://<EC2_PUBLIC_IP>:8080/health"
echo "WebSocket endpoint: ws://<EC2_PUBLIC_IP>:8081/chat/{roomId}"
echo ""
echo "To view logs: ssh -i $PEM_KEY $EC2_HOST 'tail -f chatflow.log'"
echo "To stop server: ssh -i $PEM_KEY $EC2_HOST 'pkill -f WebSocketServer'"
echo "========================================="
