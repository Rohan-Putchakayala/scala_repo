#!/bin/bash

# Visitor Management System - API Test Script

BASE_URL="http://localhost:9018"

echo "=========================================="
echo "Visitor Management System - API Test"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test 1: Check if server is running
echo -e "${BLUE}[TEST 1] Checking if server is running...${NC}"
if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/visitors/active" | grep -q "200\|404"; then
    echo -e "${GREEN}✓ Server is running${NC}"
else
    echo -e "${RED}✗ Server is not running. Start the application with 'sbt run'${NC}"
    exit 1
fi
echo ""

# Test 2: Check-in a visitor
echo -e "${BLUE}[TEST 2] Checking in a visitor...${NC}"
ID_PROOF_NUMBER="ID123456789"
CHECKIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/visitors/check-in" \
  -F "name=John Doe" \
  -F "email=john.doe@example.com" \
  -F "phoneNumber=+1234567890" \
  -F "company=TechCorp" \
  -F "purposeOfVisit=Business Meeting" \
  -F "hostEmployeeEmail=host@company.com" \
  -F "idProofNumber=$ID_PROOF_NUMBER")

if echo "$CHECKIN_RESPONSE" | grep -q "checkInId"; then
    echo -e "${GREEN}✓ Check-in successful${NC}"
    echo "Response: $CHECKIN_RESPONSE"
    
    # Extract checkInId
    CHECK_IN_ID=$(echo "$CHECKIN_RESPONSE" | grep -o '"checkInId":[0-9]*' | grep -o '[0-9]*')
    echo "Check-in ID: $CHECK_IN_ID"
else
    echo -e "${RED}✗ Check-in failed${NC}"
    echo "Response: $CHECKIN_RESPONSE"
    CHECK_IN_ID=""
fi
echo ""

# Test 3: Get active visitors
echo -e "${BLUE}[TEST 3] Getting active visitors...${NC}"
ACTIVE_RESPONSE=$(curl -s "$BASE_URL/api/visitors/active")
if echo "$ACTIVE_RESPONSE" | grep -q "checkInId"; then
    echo -e "${GREEN}✓ Active visitors retrieved${NC}"
    echo "Active visitors: $ACTIVE_RESPONSE"
else
    echo -e "${GREEN}✓ No active visitors or empty response${NC}"
    echo "Response: $ACTIVE_RESPONSE"
fi
echo ""

# Test 4: Get visitor history
echo -e "${BLUE}[TEST 4] Getting visitor history...${NC}"
HISTORY_RESPONSE=$(curl -s "$BASE_URL/api/visitors/history/john.doe@example.com")
echo "History: $HISTORY_RESPONSE"
echo ""

# Test 5: Check-out visitor (if check-in was successful)
if [ -n "$CHECK_IN_ID" ]; then
    echo -e "${BLUE}[TEST 5] Checking out visitor...${NC}"
    CHECKOUT_RESPONSE=$(curl -s -X PUT "$BASE_URL/api/visitors/check-out/$CHECK_IN_ID")
    if echo "$CHECKOUT_RESPONSE" | grep -q "checkOutTime"; then
        echo -e "${GREEN}✓ Check-out successful${NC}"
        echo "Response: $CHECKOUT_RESPONSE"
    else
        echo -e "${RED}✗ Check-out failed${NC}"
        echo "Response: $CHECKOUT_RESPONSE"
    fi
else
    echo -e "${BLUE}[TEST 5] Skipping check-out test (no check-in ID)${NC}"
fi
echo ""

echo "=========================================="
echo "Test completed!"
echo "=========================================="
