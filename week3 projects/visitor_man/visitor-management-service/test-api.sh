#!/bin/bash

# Visitor Management Service API Test Script
# This script tests all the API endpoints of the consolidated visitor management service

set -e

echo "=========================================="
echo "Visitor Management Service API Test"
echo "=========================================="

# Service configuration
SERVICE_URL="${SERVICE_URL:-http://localhost:9005}"
AUTH_USERNAME="${AUTH_USERNAME:-admin}"
AUTH_PASSWORD="${AUTH_PASSWORD:-admin123}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "PASS") echo -e "${GREEN}✓ PASS${NC}: $message" ;;
        "FAIL") echo -e "${RED}✗ FAIL${NC}: $message" ;;
        "INFO") echo -e "${BLUE}ℹ INFO${NC}: $message" ;;
        "WARN") echo -e "${YELLOW}⚠ WARN${NC}: $message" ;;
    esac
}

# Function to make HTTP requests and check response
test_endpoint() {
    local method=$1
    local endpoint=$2
    local data=$3
    local expected_status=$4
    local headers=$5
    local description=$6

    echo ""
    print_status "INFO" "Testing: $description"
    echo "  → $method $SERVICE_URL$endpoint"

    if [[ -n $headers && -n $data ]]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X $method \
            -H "Content-Type: application/json" \
            -H "$headers" \
            -d "$data" \
            "$SERVICE_URL$endpoint")
    elif [[ -n $headers ]]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X $method \
            -H "Content-Type: application/json" \
            -H "$headers" \
            "$SERVICE_URL$endpoint")
    elif [[ -n $data ]]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X $method \
            -H "Content-Type: application/json" \
            -d "$data" \
            "$SERVICE_URL$endpoint")
    else
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X $method \
            "$SERVICE_URL$endpoint")
    fi

    http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    body=$(echo $response | sed -e 's/HTTPSTATUS:.*//g')

    if [[ $http_code -eq $expected_status ]]; then
        print_status "PASS" "Status: $http_code (expected: $expected_status)"
        if [[ -n $body ]]; then
            echo "  Response: $body" | head -c 200
            [[ ${#body} -gt 200 ]] && echo "..."
            echo ""
        fi
        return 0
    else
        print_status "FAIL" "Status: $http_code (expected: $expected_status)"
        echo "  Response: $body"
        return 1
    fi
}

# Initialize test counters
total_tests=0
passed_tests=0

run_test() {
    total_tests=$((total_tests + 1))
    if "$@"; then
        passed_tests=$((passed_tests + 1))
    fi
}

echo ""
print_status "INFO" "Starting API tests against $SERVICE_URL"

# Test 1: Health Check
echo ""
echo "═══════════════════════════════════════"
echo "Test 1: Health Check"
echo "═══════════════════════════════════════"
run_test test_endpoint "GET" "/health" "" "200" "" "Health check endpoint"

# Test 2: Home Page
echo ""
echo "═══════════════════════════════════════"
echo "Test 2: Home Page"
echo "═══════════════════════════════════════"
run_test test_endpoint "GET" "/" "" "200" "" "Home page"

# Test 3: Authentication - Login with valid credentials
echo ""
echo "═══════════════════════════════════════"
echo "Test 3: Authentication"
echo "═══════════════════════════════════════"

login_data='{
    "username": "'$AUTH_USERNAME'",
    "password": "'$AUTH_PASSWORD'"
}'

print_status "INFO" "Attempting login to get JWT token..."
response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$login_data" \
    "$SERVICE_URL/auth/login")

http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
body=$(echo $response | sed -e 's/HTTPSTATUS:.*//g')

if [[ $http_code -eq 200 ]]; then
    JWT_TOKEN=$(echo $body | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    if [[ -n $JWT_TOKEN ]]; then
        print_status "PASS" "Login successful, JWT token obtained"
        echo "  Token: ${JWT_TOKEN:0:50}..."
        total_tests=$((total_tests + 1))
        passed_tests=$((passed_tests + 1))
    else
        print_status "FAIL" "Login successful but no token in response"
        echo "  Response: $body"
        total_tests=$((total_tests + 1))
        exit 1
    fi
else
    print_status "FAIL" "Login failed with status: $http_code"
    echo "  Response: $body"
    total_tests=$((total_tests + 1))
    exit 1
fi

# Test 4: Authentication - Login with invalid credentials
invalid_login_data='{
    "username": "invalid",
    "password": "invalid"
}'
run_test test_endpoint "POST" "/auth/login" "$invalid_login_data" "401" "" "Login with invalid credentials"

# Test 5: Visitor Check-in (Valid)
echo ""
echo "═══════════════════════════════════════"
echo "Test 5: Visitor Management - Check-in"
echo "═══════════════════════════════════════"

checkin_data='{
    "visitor": {
        "firstName": "John",
        "lastName": "Doe",
        "email": "john.doe@example.com",
        "phone": "+1234567890"
    },
    "hostEmployeeId": 1,
    "purpose": "Business Meeting",
    "idProof": {
        "aadhaarNumber": "123456789012"
    }
}'

auth_header="Authorization: Bearer $JWT_TOKEN"
run_test test_endpoint "POST" "/api/visitors/checkin" "$checkin_data" "201" "$auth_header" "Valid visitor check-in"

# Test 6: Visitor Check-in (Invalid - Missing fields)
invalid_checkin_data='{
    "visitor": {
        "firstName": "Jane"
    },
    "purpose": "Meeting"
}'

run_test test_endpoint "POST" "/api/visitors/checkin" "$invalid_checkin_data" "400" "$auth_header" "Invalid check-in (missing fields)"

# Test 7: Visitor Check-in (Invalid - Bad Aadhaar)
invalid_aadhaar_data='{
    "visitor": {
        "firstName": "Jane",
        "lastName": "Smith",
        "email": "jane@example.com"
    },
    "hostEmployeeId": 1,
    "purpose": "Meeting",
    "idProof": {
        "aadhaarNumber": "invalid"
    }
}'

run_test test_endpoint "POST" "/api/visitors/checkin" "$invalid_aadhaar_data" "400" "$auth_header" "Invalid check-in (bad Aadhaar format)"

# Test 8: Get Active Visits
echo ""
echo "═══════════════════════════════════════"
echo "Test 8: Active Visits"
echo "═══════════════════════════════════════"
run_test test_endpoint "GET" "/api/visits/active" "" "200" "$auth_header" "Get active visits"

# Test 9: Get Visitor Details (assuming visitor ID 1 exists)
echo ""
echo "═══════════════════════════════════════"
echo "Test 9: Visitor Details"
echo "═══════════════════════════════════════"
run_test test_endpoint "GET" "/api/visitors/1" "" "200,404" "$auth_header" "Get visitor details (ID: 1)"

# Test 10: Visitor Checkout (assuming visit ID 1 exists)
echo ""
echo "═══════════════════════════════════════"
echo "Test 10: Visitor Check-out"
echo "═══════════════════════════════════════"
run_test test_endpoint "POST" "/api/visitors/checkout/1" "" "200,404" "$auth_header" "Visitor check-out (Visit ID: 1)"

# Test 11: Unauthorized access (no token)
echo ""
echo "═══════════════════════════════════════"
echo "Test 11: Security - Unauthorized Access"
echo "═══════════════════════════════════════"
run_test test_endpoint "GET" "/api/visits/active" "" "401" "" "Unauthorized access (no token)"

# Test 12: Invalid token
echo ""
echo "═══════════════════════════════════════"
echo "Test 12: Security - Invalid Token"
echo "═══════════════════════════════════════"
invalid_auth_header="Authorization: Bearer invalid.token.here"
run_test test_endpoint "GET" "/api/visits/active" "" "401" "$invalid_auth_header" "Invalid token access"

# Performance Test: Multiple concurrent requests
echo ""
echo "═══════════════════════════════════════"
echo "Test 13: Performance - Concurrent Requests"
echo "═══════════════════════════════════════"

print_status "INFO" "Running 10 concurrent health check requests..."
start_time=$(date +%s.%N)

for i in {1..10}; do
    curl -s "$SERVICE_URL/health" > /dev/null &
done
wait

end_time=$(date +%s.%N)
duration=$(echo "$end_time - $start_time" | bc)
print_status "PASS" "10 concurrent requests completed in ${duration}s"
total_tests=$((total_tests + 1))
passed_tests=$((passed_tests + 1))

# Test Results Summary
echo ""
echo "=========================================="
echo "TEST RESULTS SUMMARY"
echo "=========================================="
echo ""
echo "Total Tests: $total_tests"
echo "Passed: $passed_tests"
echo "Failed: $((total_tests - passed_tests))"
echo ""

if [[ $passed_tests -eq $total_tests ]]; then
    print_status "PASS" "ALL TESTS PASSED! 🎉"
    echo ""
    echo "The Visitor Management Service is working correctly!"
    echo "Service URL: $SERVICE_URL"
    echo ""
    exit 0
else
    print_status "FAIL" "$((total_tests - passed_tests)) test(s) failed"
    echo ""
    echo "Please check the service logs and fix the issues."
    echo ""
    exit 1
fi

# Additional Information
echo "=========================================="
echo "ADDITIONAL INFORMATION"
echo "=========================================="
echo ""
echo "API Endpoints Available:"
echo "  POST /auth/login                    - Authenticate and get JWT token"
echo "  GET  /health                        - Health check"
echo "  GET  /                              - Home page"
echo "  POST /api/visitors/checkin          - Check-in a visitor"
echo "  POST /api/visitors/checkout/:id     - Check-out a visitor"
echo "  GET  /api/visitors/:id              - Get visitor details"
echo "  GET  /api/visits/active             - Get active visits"
echo ""
echo "Authentication:"
echo "  Username: $AUTH_USERNAME"
echo "  Password: $AUTH_PASSWORD"
echo "  Token format: Bearer <JWT_TOKEN>"
echo ""
echo "Service Configuration:"
echo "  URL: $SERVICE_URL"
echo "  Port: 9005"
echo ""
