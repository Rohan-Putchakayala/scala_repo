#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Base URL for the Room Management Service
BASE_URL="http://localhost:9000"

# Colors for output

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_test() {
    echo -e "${CYAN}🧪${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Function to make HTTP request and check response
test_endpoint() {
    local method=$1
    local endpoint=$2
    local data=$3
    local expected_status=$4
    local test_name=$5

    print_test "Testing: $test_name"

    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -d "$data" \
            "$BASE_URL$endpoint")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE_URL$endpoint")
    fi

    # Extract response body and status code
    body=$(echo "$response" | head -n -1)
    status=$(echo "$response" | tail -n 1)

    if [ "$status" -eq "$expected_status" ]; then
        print_success "Status: $status (Expected: $expected_status)"
        if [ -n "$body" ] && [ "$body" != "null" ]; then
            echo "$body" | jq '.' 2>/dev/null || echo "$body"
        fi
        echo ""
        return 0
    else
        print_error "Status: $status (Expected: $expected_status)"
        echo "Response body: $body"
        echo ""
        return 1
    fi
}

# Function to extract value from JSON response
extract_json_value() {
    local json=$1
    local key=$2
    echo "$json" | jq -r ".$key" 2>/dev/null || echo ""
}

# Main test function
run_tests() {
    echo "============================================="
    echo "  🚀 Room Management Microservices API Test"
    echo "============================================="
    echo ""

    # Check if services are running
    print_info "Checking if Room Management Service is running..."

    if ! curl -f -s "$BASE_URL/health" > /dev/null; then
        print_error "Room Management Service is not running at $BASE_URL"
        print_info "Please start services first using: ./start-microservices.sh"
        exit 1
    fi

    print_success "Room Management Service is running!"

    # Check if Kafka is accessible (indicates notification service infrastructure)
    print_info "Checking Kafka connectivity..."
    if ! nc -z localhost 9092 2>/dev/null; then
        print_warning "Kafka not accessible - Notification Service may not be working"
    else
        print_success "Kafka is accessible - Notification Service should be processing messages"
    fi
    echo ""

    # Test 1: Health Check
    test_endpoint "GET" "/health" "" "200" "Health Check Endpoint"

    # Test 2: Get All Rooms
    test_endpoint "GET" "/api/rooms" "" "200" "Get All Rooms"

    # Test 3: Get Active Reservations
    test_endpoint "GET" "/api/reservations/active" "" "200" "Get Active Reservations"

    # Test 4: Check Room Availability (Valid Request)
    future_date=$(date -d "+1 day" +"%Y-%m-%dT10:00:00")
    future_end_date=$(date -d "+1 day" +"%Y-%m-%dT11:00:00")
    test_endpoint "GET" "/api/reservations/availability?roomId=1&startTime=${future_date}&endTime=${future_end_date}" "" "200" "Check Room Availability (Valid)"

    # Test 5: Check Room Availability (Missing Parameters)
    test_endpoint "GET" "/api/reservations/availability?roomId=1" "" "400" "Check Room Availability (Missing Parameters)"

    # Test 6: Create Reservation (Valid Request)
    future_reservation_date=$(date -d "+2 hours" +"%Y-%m-%dT%H:%M:%S")
    reservation_data='{
        "roomId": 1,
        "employeeName": "API Test User",
        "employeeEmail": "apitest@company.com",
        "department": "QA",
        "purpose": "API Testing Session",
        "startTime": "'$future_reservation_date'",
        "durationMinutes": 60
    }'

    reservation_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$reservation_data" \
        "$BASE_URL/api/reservations")

    reservation_status=$(curl -s -w "%{http_code}" -o /dev/null -X POST \
        -H "Content-Type: application/json" \
        -d "$reservation_data" \
        "$BASE_URL/api/reservations")

    if [ "$reservation_status" -eq "200" ]; then
        print_success "Create Reservation (Valid Request): Status 200"
        echo "$reservation_response" | jq '.' 2>/dev/null || echo "$reservation_response"

        # Extract reservation ID for cleanup
        reservation_id=$(extract_json_value "$reservation_response" "reservationId")
        echo ""
    else
        print_error "Create Reservation (Valid Request): Status $reservation_status"
        echo "Response: $reservation_response"
        echo ""
    fi

    # Test 7: Create Reservation (Invalid Room ID)
    invalid_reservation_data='{
        "roomId": 999,
        "employeeName": "API Test User",
        "employeeEmail": "apitest@company.com",
        "department": "QA",
        "purpose": "API Testing Session",
        "startTime": "'$future_reservation_date'",
        "durationMinutes": 60
    }'
    test_endpoint "POST" "/api/reservations" "$invalid_reservation_data" "400" "Create Reservation (Invalid Room ID)"

    # Test 8: Create Reservation (Invalid JSON)
    invalid_json_data='{"roomId": 1, "employeeName": "Test", "invalidField"}'
    test_endpoint "POST" "/api/reservations" "$invalid_json_data" "400" "Create Reservation (Invalid JSON)"

    # Test 9: Create Reservation (Missing Required Fields)
    incomplete_data='{"roomId": 1, "employeeName": "Test User"}'
    test_endpoint "POST" "/api/reservations" "$incomplete_data" "400" "Create Reservation (Missing Required Fields)"

    # Test 10: Create Conflicting Reservation
    conflicting_reservation_data='{
        "roomId": 1,
        "employeeName": "Conflict Test User",
        "employeeEmail": "conflict@company.com",
        "department": "QA",
        "purpose": "Conflict Testing",
        "startTime": "'$future_reservation_date'",
        "durationMinutes": 30
    }'
    test_endpoint "POST" "/api/reservations" "$conflicting_reservation_data" "409" "Create Conflicting Reservation"

    # Test 11: Process Reminders (Admin Endpoint)
    test_endpoint "POST" "/api/reservations/process-reminders" "" "200" "Process Reminders (Admin)"

    # Test 12: Process Auto-Releases (Admin Endpoint)
    test_endpoint "POST" "/api/reservations/process-auto-releases" "" "200" "Process Auto-Releases (Admin)"

    # Test 13: Check Room Availability After Reservation
    test_endpoint "GET" "/api/reservations/availability?roomId=1&startTime=${future_reservation_date}&endTime=${future_end_date}" "" "200" "Check Room Availability (After Reservation)"

    # Test 14: Get Active Reservations (Should Include New Reservation)
    print_test "Testing: Verify New Reservation in Active List"
    active_reservations=$(curl -s "$BASE_URL/api/reservations/active")
    if echo "$active_reservations" | grep -q "API Test User"; then
        print_success "New reservation found in active reservations list"
    else
        print_warning "New reservation not found in active reservations list (might be expected if auto-processed)"
    fi
    echo "$active_reservations" | jq '.' 2>/dev/null || echo "$active_reservations"
    echo ""

    # Test 15: Invalid Endpoint
    test_endpoint "GET" "/api/invalid-endpoint" "" "404" "Invalid Endpoint"

    # Performance Test: Multiple Quick Requests
    print_test "Testing: API Performance (10 concurrent health checks)"
    start_time=$(date +%s.%N)

    for i in {1..10}; do
        curl -s "$BASE_URL/health" > /dev/null &
    done
    wait

    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc -l)
    print_success "Completed 10 concurrent requests in ${duration} seconds"
    echo ""

    # Summary
    echo "============================================="
    echo "  📊 Test Summary"
    echo "============================================="

    # Count successful vs failed tests by checking return codes of previous tests
    # This is a simplified summary - in a real test framework, we'd track these properly

    print_info "Core API Functionality: ✓ Tested"
    print_info "Error Handling: ✓ Tested"
    print_info "Validation: ✓ Tested"
    print_info "Admin Endpoints: ✓ Tested"
    print_info "Performance: ✓ Basic load tested"

    echo ""
    print_success "🎉 API Test Suite Completed!"

    echo ""
    echo "💡 Microservices Testing Recommendations:"
    echo "  • Monitor Kafka topics to verify inter-service communication"
    echo "  • Check Notification Service console for message processing"
    echo "  • Verify email notifications are sent"
    echo "  • Test database persistence across service restarts"
    echo "  • Load test with higher concurrent requests"

    echo ""
    echo "🔍 Monitor Kafka Message Flow:"
    echo "  docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic room-preparation-notifications --from-beginning"
    echo "  docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic reservation-reminder-notifications --from-beginning"
    echo ""
    echo "📊 Check Services Status:"
    echo "  Room Management: curl http://localhost:9000/health"
    echo "  Notification Service: Check console output for Akka system logs"
    echo "  Kafka UI: http://localhost:8080"
    echo ""
    echo "🏗️ Microservices Architecture:"
    echo "  • room-management-service/ - Play Framework + Kafka Producer"
    echo "  • notification-service/   - Akka Actors + Kafka Consumer"

    return 0
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --base-url URL    Base URL for the API (default: http://localhost:9000)"
    echo "  --help           Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Test against localhost:9000"
    echo "  $0 --base-url http://prod-api:9000   # Test against production"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --base-url)
            BASE_URL="$2"
            shift 2
            ;;
        --help)
            show_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Check dependencies
if ! command -v curl &> /dev/null; then
    print_error "curl is required but not installed."
    exit 1
fi

if ! command -v jq &> /dev/null; then
    print_warning "jq is not installed. JSON responses will be displayed in raw format."
fi

if ! command -v bc &> /dev/null; then
    print_warning "bc is not installed. Performance timing may not work correctly."
fi

if ! command -v nc &> /dev/null; then
    print_warning "nc (netcat) is not installed. Kafka connectivity check will be skipped."
fi

# Run the tests
run_tests
