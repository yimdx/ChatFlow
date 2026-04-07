#!/bin/bash

set -euo pipefail

# PostgreSQL setup script for Ubuntu EC2.
# This creates database/user for ChatFlow and enables remote access in VPC.

# Usage:
#   ./setup-postgres.sh --config ./config/postgres.env

CONFIG_FILE=""

if [ "${1:-}" = "--config" ] || [ "${1:-}" = "-c" ]; then
  if [ "$#" -lt 2 ]; then
    echo "Usage: $0 --config <config-file>"
    exit 1
  fi
  CONFIG_FILE=$2
  shift 2
fi

if [ -n "$CONFIG_FILE" ]; then
  if [ ! -f "$CONFIG_FILE" ]; then
    echo "Config file not found: $CONFIG_FILE"
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$CONFIG_FILE"
  set +a
fi

DB_NAME=${DB_NAME:-chatflow}
DB_USER=${DB_USER:-chatflow}
DB_PASSWORD=${DB_PASSWORD:-chatflow}
PG_VERSION=${PG_VERSION:-16}
ALLOW_CIDR=${ALLOW_CIDR:-10.0.0.0/8}

echo "Installing PostgreSQL ${PG_VERSION}..."
sudo apt-get update -y
sudo apt-get install -y "postgresql-${PG_VERSION}" "postgresql-client-${PG_VERSION}"

echo "Enabling and starting PostgreSQL..."
sudo systemctl enable postgresql
sudo systemctl start postgresql

PG_CONF="/etc/postgresql/${PG_VERSION}/main/postgresql.conf"
PG_HBA="/etc/postgresql/${PG_VERSION}/main/pg_hba.conf"

echo "Configuring PostgreSQL listen addresses and pg_hba..."
sudo sed -i "s/^#listen_addresses =.*/listen_addresses = '*'/'" "$PG_CONF"
if ! sudo grep -q "host\s\+${DB_NAME}\s\+${DB_USER}\s\+${ALLOW_CIDR}\s\+scram-sha-256" "$PG_HBA"; then
  echo "host ${DB_NAME} ${DB_USER} ${ALLOW_CIDR} scram-sha-256" | sudo tee -a "$PG_HBA" >/dev/null
fi

sudo systemctl restart postgresql

echo "Creating database and user..."
sudo -u postgres psql <<SQL
DO
\$\$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${DB_USER}') THEN
      CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';
   END IF;
END
\$\$;

CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};
\c ${DB_NAME}
GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};
SQL

PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || true)
PRIVATE_IP=$(hostname -I | awk '{print $1}')

echo "PostgreSQL setup complete"
echo "DB name: ${DB_NAME}"
echo "DB user: ${DB_USER}"
echo "Private IP: ${PRIVATE_IP}"
if [ -n "${PUBLIC_IP}" ]; then
  echo "Public IP: ${PUBLIC_IP}"
fi
echo "Connection string: jdbc:postgresql://${PRIVATE_IP}:5432/${DB_NAME}"
echo "Security group: allow inbound TCP 5432 only from app instances"
