#!/bin/bash

# RabbitMQ Setup Script for Ubuntu EC2
echo "Installing RabbitMQ on Ubuntu EC2..."

# Update packages
sudo apt-get update -y

# Install dependencies
sudo apt-get install -y curl gnupg apt-transport-https

# Add RabbitMQ signing key
curl -fsSL https://github.com/rabbitmq/signing-keys/releases/download/2.0/rabbitmq-release-signing-key.asc | sudo apt-key add -

# Add RabbitMQ repository
sudo tee /etc/apt/sources.list.d/bintray.rabbitmq.list <<EOF
deb https://dl.bintray.com/rabbitmq-erlang/debian focal erlang-23.x
deb https://dl.bintray.com/rabbitmq/debian focal main
EOF

# Update package lists
sudo apt-get update -y

# Install Erlang
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
