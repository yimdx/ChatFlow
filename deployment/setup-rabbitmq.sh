#!/bin/bash

# RabbitMQ Setup Script for Ubuntu 24.04 EC2
echo "Installing RabbitMQ on Ubuntu 24.04 EC2..."

# Update packages
sudo apt-get update -y

# Install dependencies
sudo apt-get install -y curl gnupg apt-transport-https

# Add RabbitMQ signing keys (CloudSmith - official current repo)
curl -1sLf 'https://keys.openpgp.org/vks/v1/by-fingerprint/0A9AF2115F4687BD29803A206B73A36E6026DFCA' | sudo gpg --dearmor -o /usr/share/keyrings/com.rabbitmq.team.gpg
curl -1sLf 'https://keyserver.ubuntu.com/pks/lookup?op=get&search=0xf77f1eda57ebb1cc' | sudo gpg --dearmor -o /usr/share/keyrings/net.launchpad.ppa.rabbitmq.erlang.gpg
curl -1sLf 'https://packagecloud.io/rabbitmq/rabbitmq-server/gpgkey' | sudo gpg --dearmor -o /usr/share/keyrings/io.packagecloud.rabbitmq.gpg

# Add RabbitMQ and Erlang repositories (Ubuntu 24.04 / noble)
sudo tee /etc/apt/sources.list.d/rabbitmq.list <<EOF
deb [arch=amd64 signed-by=/usr/share/keyrings/net.launchpad.ppa.rabbitmq.erlang.gpg] http://ppa.launchpad.net/rabbitmq/rabbitmq-erlang/ubuntu noble main
deb [arch=amd64 signed-by=/usr/share/keyrings/io.packagecloud.rabbitmq.gpg] https://packagecloud.io/rabbitmq/rabbitmq-server/ubuntu noble main
EOF

# Update package lists
sudo apt-get update -y

# Install Erlang (pinned to a version compatible with RabbitMQ 3.12+)
sudo apt-get install -y erlang-base \
                        erlang-asn1 erlang-crypto erlang-eldap erlang-ftp erlang-inets \
                        erlang-mnesia erlang-os-mon erlang-parsetools erlang-public-key \
                        erlang-runtime-tools erlang-snmp erlang-ssl \
                        erlang-syntax-tools erlang-tftp erlang-tools erlang-xmerl

# Install RabbitMQ
sudo apt-get install -y rabbitmq-server

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

echo "RabbitMQ installation complete!"
echo "Management Console: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):15672"
echo "Username: admin"
echo "Password: adminpassword"
echo ""
echo "IMPORTANT: Change the admin password in production!"
echo "IMPORTANT: Configure security groups to allow:"
echo "  - Port 5672 (AMQP)"
echo "  - Port 15672 (Management Console)"
