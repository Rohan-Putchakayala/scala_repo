#!/bin/bash

# Visitor Management System - Startup Script
# This script helps you start all required services

set -e

echo "=========================================="
echo "Visitor Management System - Startup"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check Java version
echo -e "${YELLOW}Step 1: Checking Java version...${NC}"
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -ge 17 ] && [ "$JAVA_VERSION" -le 21 ]; then
    echo -e "${GREEN}✓ Java $JAVA_VERSION detected (OK)${NC}"
else
    echo -e "${RED}✗ Java version $JAVA_VERSION detected. Need Java 17 or 21${NC}"
    echo "Please run: export JAVA_HOME=\$(/usr/libexec/java_home -v 21)"
    exit 1
fi

# Check if Kafka is running
echo -e "${YELLOW}Step 2: Checking Kafka...${NC}"
if lsof -Pi :9092 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo -e "${GREEN}✓ Kafka is running on port 9092${NC}"
else
    echo -e "${RED}✗ Kafka is NOT running on port 9092${NC}"
    echo ""
    echo "Please start Kafka first:"
    echo "  Option 1: kafka-server-start /opt/homebrew/etc/kafka/server.properties"
    echo "  Option 2: docker-compose up -d"
    echo ""
    read -p "Press ENTER after starting Kafka, or Ctrl+C to exit..."
    
    # Check again
    if lsof -Pi :9092 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        echo -e "${GREEN}✓ Kafka is now running${NC}"
    else
        echo -e "${RED}✗ Kafka still not running. Exiting.${NC}"
        exit 1
    fi
fi

# Check if port 9000 is available
echo -e "${YELLOW}Step 3: Checking port 9000...${NC}"
if lsof -Pi :9000 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo -e "${YELLOW}⚠ Port 9000 is already in use${NC}"
    read -p "Kill process on port 9000? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        lsof -ti:9000 | xargs kill -9
        echo -e "${GREEN}✓ Port 9000 freed${NC}"
    else
        echo -e "${RED}✗ Cannot start on port 9000. Exiting.${NC}"
        exit 1
    fi
else
    echo -e "${GREEN}✓ Port 9000 is available${NC}"
fi

echo ""
echo -e "${GREEN}=========================================="
echo "All prerequisites checked!"
echo "==========================================${NC}"
echo ""
echo "Starting Play Framework application..."
echo "Press Ctrl+C to stop"
echo ""

# Start Play application
cd "$(dirname "$0")"
sbt run

