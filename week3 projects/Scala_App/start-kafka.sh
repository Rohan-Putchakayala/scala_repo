#!/bin/bash

# Kafka Startup Script
# Helps you start Kafka easily

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}Kafka Startup Script${NC}"
echo ""

# Check if Kafka is already running
if lsof -Pi :9092 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo -e "${GREEN}✓ Kafka is already running on port 9092${NC}"
    exit 0
fi

# Try to find Kafka installation
echo "Looking for Kafka installation..."

# Common locations
KAFKA_PATHS=(
    "/opt/homebrew/opt/kafka/bin/kafka-server-start"
    "/usr/local/opt/kafka/bin/kafka-server-start"
    "/opt/kafka/bin/kafka-server-start"
    "$HOME/kafka/bin/kafka-server-start"
)

KAFKA_CONFIG_PATHS=(
    "/opt/homebrew/etc/kafka/server.properties"
    "/usr/local/etc/kafka/server.properties"
    "/opt/kafka/config/server.properties"
    "$HOME/kafka/config/server.properties"
)

KAFKA_BIN=""
KAFKA_CONFIG=""

# Find Kafka binary
for path in "${KAFKA_PATHS[@]}"; do
    if [ -f "$path" ]; then
        KAFKA_BIN="$path"
        echo -e "${GREEN}✓ Found Kafka at: $KAFKA_BIN${NC}"
        break
    fi
done

# Find Kafka config
for path in "${KAFKA_CONFIG_PATHS[@]}"; do
    if [ -f "$path" ]; then
        KAFKA_CONFIG="$path"
        echo -e "${GREEN}✓ Found Kafka config at: $KAFKA_CONFIG${NC}"
        break
    fi
done

# Check if Docker is available
if command -v docker &> /dev/null; then
    echo ""
    echo "Docker detected. Would you like to use Docker instead? (easier)"
    read -p "Use Docker? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Starting Kafka with Docker..."
        
        # Check if docker-compose.yml exists
        if [ -f "docker-compose.yml" ]; then
            docker-compose up -d
            echo -e "${GREEN}✓ Kafka started with Docker Compose${NC}"
        else
            echo "Starting Kafka container..."
            docker run -d \
                --name kafka \
                -p 9092:9092 \
                -e KAFKA_ZOOKEEPER_CONNECT=localhost:2181 \
                apache/kafka:latest 2>/dev/null || {
                echo -e "${YELLOW}Container might already exist. Starting existing container...${NC}"
                docker start kafka
            }
            echo -e "${GREEN}✓ Kafka started with Docker${NC}"
        fi
        
        echo ""
        echo "Waiting for Kafka to be ready..."
        sleep 5
        
        if lsof -Pi :9092 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
            echo -e "${GREEN}✓ Kafka is running on port 9092${NC}"
        else
            echo -e "${RED}✗ Kafka failed to start${NC}"
            exit 1
        fi
        exit 0
    fi
fi

# If Kafka binary found, use it
if [ -n "$KAFKA_BIN" ] && [ -n "$KAFKA_CONFIG" ]; then
    echo ""
    echo "Starting Kafka..."
    echo "Press Ctrl+C to stop"
    echo ""
    "$KAFKA_BIN" "$KAFKA_CONFIG"
else
    echo -e "${RED}✗ Kafka not found in common locations${NC}"
    echo ""
    echo "Please install Kafka:"
    echo "  macOS: brew install kafka"
    echo "  Or download from: https://kafka.apache.org/downloads"
    echo ""
    echo "Or use Docker:"
    echo "  docker run -d --name kafka -p 9092:9092 apache/kafka:latest"
    exit 1
fi

