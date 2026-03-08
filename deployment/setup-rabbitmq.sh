#!/bin/bash

# RabbitMQ Setup Script for Ubuntu 24.04 EC2
echo "Installing RabbitMQ on Ubuntu 24.04 EC2..."

# Update packages
sudo apt-get update -y

# Install dependencies
sudo apt-get install -y curl gnupg apt-transport-https software-properties-common

# Method 1: Try Ubuntu's built-in RabbitMQ (simpler, usually works)
echo "Attempting installation from Ubuntu repositories..."
sudo apt-get install -y rabbitmq-server

# Check if installation was successful
if systemctl is-active --quiet rabbitmq-server; then
    echo "RabbitMQ installed successfully from Ubuntu repositories!"
else
    echo "Ubuntu repository installation failed or RabbitMQ not active."
    echo "Attempting installation from RabbitMQ official repositories with Erlang 26.x..."
    
    # Remove any partial installation
    sudo apt-get remove -y rabbitmq-server erlang-* 2>/dev/null
    sudo apt-get autoremove -y
    
    # Add RabbitMQ signing keys
    curl -1sLf 'https://keys.openpgp.org/vks/v1/by-fingerprint/0A9AF2115F4687BD29803A206B73A36E6026DFCA' | sudo gpg --dearmor -o /usr/share/keyrings/com.rabbitmq.team.gpg
    curl -1sLf 'https://github.com/rabbitmq/signing-keys/releases/download/3.0/cloudsmith.rabbitmq-erlang.E495BB49CC4BBE5B.key' | sudo gpg --dearmor -o /usr/share/keyrings/rabbitmq.E495BB49CC4BBE5B.gpg
    curl -1sLf 'https://github.com/rabbitmq/signing-keys/releases/download/3.0/cloudsmith.rabbitmq-server.9F4587F226208342.key' | sudo gpg --dearmor -o /usr/share/keyrings/rabbitmq.9F4587F226208342.gpg
    
    # Add repositories for Erlang 26.x and RabbitMQ 3.13.x (compatible versions)
    sudo tee /etc/apt/sources.list.d/rabbitmq.list <<EOF
## Provides modern Erlang/OTP releases (26.x)
deb [arch=amd64 signed-by=/usr/share/keyrings/rabbitmq.E495BB49CC4BBE5B.gpg] https://ppa1.novemberain.com/rabbitmq/rabbitmq-erlang/deb/ubuntu noble main
deb-src [arch=amd64 signed-by=/usr/share/keyrings/rabbitmq.E495BB49CC4BBE5B.gpg] https://ppa1.novemberain.com/rabbitmq/rabbitmq-erlang/deb/ubuntu noble main

## Provides RabbitMQ
deb [arch=amd64 signed-by=/usr/share/keyrings/rabbitmq.9F4587F226208342.gpg] https://ppa1.novemberain.com/rabbitmq/rabbitmq-server/deb/ubuntu noble main
deb-src [arch=amd64 signed-by=/usr/share/keyrings/rabbitmq.9F4587F226208342.gpg] https://ppa1.novemberain.com/rabbitmq/rabbitmq-server/deb/ubuntu noble main
EOF
    
    # Update package lists
    sudo apt-get update -y
    
    # Install Erlang packages (version 26.x)
    sudo apt-get install -y erlang-base \
                            erlang-asn1 erlang-crypto erlang-eldap erlang-ftp erlang-inets \
                            erlang-mnesia erlang-os-mon erlang-parsetools erlang-public-key \
                            erlang-runtime-tools erlang-snmp erlang-ssl \
                            erlang-syntax-tools erlang-tftp erlang-tools erlang-xmerl
    
    # Install RabbitMQ
    sudo apt-get install -y rabbitmq-server
fi

# Start RabbitMQ
sudo systemctl start rabbitmq-server
sudo systemctl enable rabbitmq-server

# Enable management plugin
sudo rabbitmq-plugins enable rabbitmq_management

# Create admin user (change password in production!)
sudo rabbitmqctl add_user admin adminpassword
sudo rabbitmqctl set_user_tags admin administrator
sudo rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"

# Delete default guest user (security best practice)
# sudo rabbitmqctl delete_user guest

# Get public IP (try multiple methods)
PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || curl -s ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')

echo "RabbitMQ installation complete!"
echo "Management Console: http://${PUBLIC_IP}:15672"
echo "Username: admin"
echo "Password: adminpassword"
echo ""
echo "IMPORTANT: Change the admin password in production!"
echo "IMPORTANT: Configure security groups to allow:"
echo "  - Port 5672 (AMQP)"
echo "  - Port 15672 (Management Console)"
