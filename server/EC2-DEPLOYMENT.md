# EC2 Deployment Guide

## Prerequisites

1. **EC2 Instance**: t2.micro (free tier eligible), Amazon Linux 2 or Amazon Linux 2023
2. **Security Group**: Open ports 8080, 8081, and 22
3. **SSH Key**: Your .pem key file

## Launch EC2 Instance

1. Go to AWS Console → EC2 → Launch Instance
2. **AMI**: Amazon Linux 2023 (or Amazon Linux 2)
3. **Instance Type**: t2.micro (free tier eligible)
4. **Key pair**: Create or select existing .pem key
5. **Network settings**: Create/select security group with rules below

## Security Group Configuration

In AWS Console → EC2 → Security Groups → Your instance's security group:

```
Inbound Rules:
- Type: Custom TCP, Port: 8080, Source: 0.0.0.0/0, Description: REST health endpoint
- Type: Custom TCP, Port: 8081, Source: 0.0.0.0/0, Description: WebSocket chat
- Type: SSH, Port: 22, Source: Your IP, Description: SSH access
```

## Quick Deployment

### Option 1: Using Deploy Script (Recommended)

```bash
# Make scripts executable
chmod +x deploy-to-ec2.sh ec2-setup.sh

# Deploy (replace with your EC2 details)
./deploy-to-ec2.sh ec2-user@YOUR_EC2_IP ~/.ssh/your-key.pem
```

### Option 2: Manual Deployment

```bash
# 1. Upload JAR to EC2
scp -i ~/.ssh/your-key.pem target/WebSocketServer-1.0-SNAPSHOT.jar ec2-user@YOUR_EC2_IP:~/

# 2. SSH into EC2
ssh -i ~/.ssh/your-key.pem ec2-user@YOUR_EC2_IP

# 3. Install Java 17 (Java 11+ required)
sudo yum update -y
sudo yum install -y java-17-amazon-corretto-headless screen

# 4. Run server in persistent screen session
screen -dmS chatflow bash -c 'java -jar WebSocketServer-1.0-SNAPSHOT.jar 2>&1 | tee chatflow.log'

# 5. Verify it's running
curl http://localhost:8080/health
# Should return: {"status":"healthy"}
```

## Verify Deployment

```bash
# From your local machine:
curl http://YOUR_EC2_IP:8080/health

# Should return: {"status":"healthy"}
```

## Update Client Configuration

Update your client code to use EC2 public IP:

```java
private static final String SERVER_URL = "ws://YOUR_EC2_PUBLIC_IP:8081";
```

Then rebuild clients:
```bash
cd client-part1 && mvn clean package -DskipTests
cd ../client-part2 && mvn clean package -DskipTests
```

## Server Management Commands

### Using Screen (Persistent Sessions)

```bash
# View live logs
tail -f ~/chatflow.log

# Attach to running server (see live output)
screen -r chatflow

# Detach from screen (server keeps running)
# Press: Ctrl+A, then D

# Check if server is running
pgrep -f WebSocketServer

# Stop server
screen -S chatflow -X quit

# Restart server
screen -S chatflow -X quit
screen -dmS chatflow bash -c 'java -jar WebSocketServer-1.0-SNAPSHOT.jar 2>&1 | tee chatflow.log'

# List all screen sessions
screen -ls
```

### Alternative: Using systemd Service

Create `/etc/systemd/system/chatflow.service`:

```ini
[Unit]
Description=ChatFlow WebSocket Server
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user
ExecStart=/usr/bin/java -jar /home/ec2-user/WebSocketServer-1.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:/home/ec2-user/chatflow.log
StandardError=append:/home/ec2-user/chatflow.log

[Install]
WantedBy=multi-user.target
```

Then manage with:
```bash
sudo systemctl daemon-reload
sudo systemctl start chatflow
sudo systemctl enable chatflow  # Auto-start on boot
sudo systemctl status chatflow
sudo systemctl stop chatflow
```

## Troubleshooting

### Server won't start
```bash
# Check logs
cat ~/chatflow.log

# Check if ports are already in use
sudo netstat -tulpn | grep -E '8080|8081'
```

### Can't connect from client
1. Verify security group has ports 8080 and 8081 open
2. Check server is running: `pgrep -f WebSocketServer`
3. Test health endpoint: `curl http://YOUR_EC2_IP:8080/health`
4. Verify you're using public IP, not private IP

### Connection timeout
- Check EC2 security group inbound rules
- Verify you're using the public IP (not private)
- Check if AWS Network ACL allows traffic
