#!/bin/bash

set -e

echo "============================================="
echo "  Room Management Microservices Startup"
echo "============================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# Check if sbt is installed
if ! command -v sbt &> /dev/null; then
    print_error "SBT is not installed. Please install SBT first."
    exit 1
fi

# Check if Docker is installed and running
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi

if ! docker info &> /dev/null; then
    print_error "Docker is not running. Please start Docker first."
    exit 1
fi

# Function to wait for service to be ready
wait_for_service() {
    local service_name=$1
    local url=$2
    local max_attempts=30
    local attempt=1

    print_status "Waiting for $service_name to be ready..."

    while [ $attempt -le $max_attempts ]; do
        if curl -f -s "$url" > /dev/null 2>&1; then
            print_status "$service_name is ready!"
            return 0
        fi

        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done

    print_error "$service_name failed to start within timeout"
    return 1
}

# Clean up function
cleanup() {
    print_warning "Shutting down services..."
    kill $ROOM_SERVICE_PID 2>/dev/null || true
    kill $NOTIFICATION_SERVICE_PID 2>/dev/null || true
    docker-compose down
    exit 0
}

# Set up signal handlers
trap cleanup SIGINT SIGTERM

# Step 1: Start infrastructure services
print_header "Step 1: Starting infrastructure services (Kafka, MySQL)..."

print_status "Starting Kafka and MySQL with Docker Compose..."
docker-compose up -d

# Wait for Kafka to be ready
print_status "Waiting for Kafka to be ready..."
sleep 30

# Create Kafka topics
print_status "Creating Kafka topics..."
docker exec kafka kafka-topics --create --topic room-preparation-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 || true
docker exec kafka kafka-topics --create --topic reservation-reminder-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 || true
docker exec kafka kafka-topics --create --topic room-release-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 || true
docker exec kafka kafka-topics --create --topic admin-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 || true

print_status "Kafka topics created successfully!"

# List topics to verify
print_status "Available Kafka topics:"
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Wait for MySQL to be ready
print_status "Waiting for MySQL to be ready..."
sleep 20

# Step 2: Build and start Room Management Service
print_header "Step 2: Building and Starting Room Management Service..."

print_status "Building Room Management Service..."
cd room-management-service
sbt clean compile
cd ..

print_status "Starting Room Management Service on port 9000..."
export APPLICATION_SECRET="room-management-dev-secret"
export KAFKA_ENABLED="true"
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"

# Start room management service in background
cd room-management-service
sbt "run 9000" &
ROOM_SERVICE_PID=$!
cd ..

# Wait for room management service to be ready
if wait_for_service "Room Management Service" "http://localhost:9000/health"; then
    print_status "Room Management Service started successfully!"
else
    print_error "Failed to start Room Management Service"
    kill $ROOM_SERVICE_PID 2>/dev/null || true
    cleanup
    exit 1
fi

# Step 3: Build and start Notification Service
print_header "Step 3: Building and Starting Notification Service..."

print_status "Building Notification Service..."
cd notification-service
sbt clean compile assembly
cd ..

print_status "Starting Notification Service..."
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
export KAFKA_ENABLED="true"

# Start notification service in background using the assembled JAR
cd notification-service
java -jar target/scala-2.13/notification-service-assembly.jar &
NOTIFICATION_SERVICE_PID=$!
cd ..

sleep 15
print_status "Notification Service started successfully!"

# Step 4: Display service information
print_header "Step 4: Service Information"

echo ""
echo "============================================="
echo "  🚀 All Services Started Successfully!"
echo "============================================="
echo ""
echo "📊 Service Endpoints:"
echo "  • Room Management API:    http://localhost:9000"
echo "  • Room Management Health: http://localhost:9000/health"
echo "  • Kafka UI:              http://localhost:8080"
echo ""
echo "📋 API Endpoints:"
echo "  • POST /api/reservations           - Create reservation"
echo "  • GET  /api/reservations/active    - Get active reservations"
echo "  • GET  /api/reservations/availability - Check room availability"
echo "  • GET  /api/rooms                  - List all rooms"
echo ""
echo "🔧 Infrastructure:"
echo "  • Kafka Broker:     localhost:9092"
echo "  • Zookeeper:        localhost:2181"
echo "  • MySQL Database:   localhost:3306"
echo ""
echo "📝 Kafka Topics:"
echo "  • room-preparation-notifications"
echo "  • reservation-reminder-notifications"
echo "  • room-release-notifications"
echo "  • admin-notifications"
echo ""
echo "🏗️ Microservices Architecture:"
echo "  • Room Management Service (Play + Kafka Producer): PID $ROOM_SERVICE_PID"
echo "  • Notification Service (Akka + Kafka Consumer): PID $NOTIFICATION_SERVICE_PID"
echo ""
echo "💡 Tips:"
echo "  • Use Kafka UI at http://localhost:8080 to monitor messages"
echo "  • Check service logs in separate terminal windows"
echo "  • Test API with: ./test-api.sh"
echo "  • Press Ctrl+C to stop all services gracefully"
echo ""
echo "============================================="

# Keep script running and monitor services
print_status "Services are running. Press Ctrl+C to stop all services."

# Monitor services
while true; do
    sleep 5

    # Check if room management service is still running
    if ! kill -0 $ROOM_SERVICE_PID 2>/dev/null; then
        print_error "Room Management Service has stopped unexpectedly!"
        cleanup
        exit 1
    fi

    # Check if notification service is still running
    if ! kill -0 $NOTIFICATION_SERVICE_PID 2>/dev/null; then
        print_error "Notification Service has stopped unexpectedly!"
        cleanup
        exit 1
    fi
done
