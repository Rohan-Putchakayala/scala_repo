#!/bin/bash

# Visitor Management Service Startup Script
# This script starts the consolidated visitor management service

set -e

echo "==================================="
echo "Visitor Management Service Startup"
echo "==================================="

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed. Please install Java 11 or higher."
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
if [[ $JAVA_VERSION -lt 11 ]]; then
    echo "Error: Java 11 or higher is required. Current version: $JAVA_VERSION"
    exit 1
fi

echo "Java version check passed: $JAVA_VERSION"

# Check if SBT is installed
if ! command -v sbt &> /dev/null; then
    echo "Error: SBT is not installed. Please install SBT."
    exit 1
fi

echo "SBT check passed"

# Set environment variables if not already set
export JAVA_OPTS="${JAVA_OPTS:--Xmx2g -Xms1g}"
export SBT_OPTS="${SBT_OPTS:--Xmx2g -Xms1g}"

# Database configuration
export DATABASE_URL="${DATABASE_URL:-jdbc:mysql://localhost:3306/visitor_management}"
export DATABASE_USER="${DATABASE_USER:-vms_user}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-vms_password}"

# Kafka configuration
export KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
export KAFKA_CHECKIN_TOPIC="${KAFKA_CHECKIN_TOPIC:-visitor.checkin}"
export KAFKA_CHECKOUT_TOPIC="${KAFKA_CHECKOUT_TOPIC:-visitor.checkout}"

# JWT configuration
export JWT_SECRET="${JWT_SECRET:-change-this-in-production-super-secret-key}"

# Play framework configuration
export PLAY_HTTP_SECRET_KEY="${PLAY_HTTP_SECRET_KEY:-change-this-play-secret-key-in-production}"

# Service configuration
export SERVICE_PORT="${SERVICE_PORT:-9005}"

echo ""
echo "Configuration:"
echo "- Service Port: $SERVICE_PORT"
echo "- Database URL: $DATABASE_URL"
echo "- Kafka Servers: $KAFKA_BOOTSTRAP_SERVERS"
echo "- Check-in Topic: $KAFKA_CHECKIN_TOPIC"
echo "- Check-out Topic: $KAFKA_CHECKOUT_TOPIC"
echo ""

# Check if dependencies are available
echo "Checking external dependencies..."

# Check MySQL connection
echo "Testing database connection..."
if command -v mysql &> /dev/null; then
    mysql -h $(echo $DATABASE_URL | sed 's/.*:\/\/\([^:]*\).*/\1/') -u $DATABASE_USER -p$DATABASE_PASSWORD -e "SELECT 1;" > /dev/null 2>&1 || {
        echo "Warning: Could not connect to database. Make sure MySQL is running and accessible."
    }
else
    echo "Warning: MySQL client not found. Cannot test database connection."
fi

# Check Kafka connection (if kafkacat/kcat is available)
echo "Testing Kafka connection..."
if command -v kcat &> /dev/null; then
    timeout 5s kcat -b $KAFKA_BOOTSTRAP_SERVERS -L > /dev/null 2>&1 || {
        echo "Warning: Could not connect to Kafka. Make sure Kafka is running and accessible."
    }
elif command -v kafkacat &> /dev/null; then
    timeout 5s kafkacat -b $KAFKA_BOOTSTRAP_SERVERS -L > /dev/null 2>&1 || {
        echo "Warning: Could not connect to Kafka. Make sure Kafka is running and accessible."
    }
else
    echo "Warning: kafkacat/kcat not found. Cannot test Kafka connection."
fi

echo ""
echo "Starting Visitor Management Service..."
echo ""

# Create logs directory if it doesn't exist
mkdir -p logs

# Start the service
exec sbt \
    -Dconfig.resource=application.conf \
    -Dhttp.port=$SERVICE_PORT \
    -Dpidfile.path=/dev/null \
    -Dplay.evolutions.db.default.autoApply=true \
    run

echo ""
echo "Service shutdown completed."
