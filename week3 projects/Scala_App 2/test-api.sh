#!/bin/bash

# Office Meeting Room Management System - API Test Script

BASE_URL="http:

echo "=========================================="
echo "Office Meeting Room Management System - API Test"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper to parse JSON with python3 if available
parse_json_field() {
  local json="$1"
  local field="$2"
  if command -v python3 >/dev/null 2>&1; then
    echo "$json" | python3 - <<PY
import json, sys
try:
    data = json.load(sys.stdin)
    if isinstance(data, dict) and "$field" in data:
        print(data["$field"])
    else:
        print("")
except Exception:
    print("")
PY
  else
    echo ""
  fi
}

# Test 1: Check if server is running
echo -e "${BLUE}[TEST 1] Checking if server is running...${NC}"
if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/reservations/active" | grep -q "200\|404"; then
    echo -e "${GREEN}✓ Server is running${NC}"
else
    echo -e "${RED}✗ Server is not running. Start the application with 'sbt run'${NC}"
    exit 1
fi
echo ""

# Test 2: List rooms
echo -e "${BLUE}[TEST 2] Fetching rooms...${NC}"
ROOMS_RESPONSE=$(curl -s "$BASE_URL/api/rooms")
echo "Rooms response: $ROOMS_RESPONSE"

ROOM_ID=""
if command -v python3 >/dev/null 2>&1; then
  ROOM_ID=$(echo "$ROOMS_RESPONSE" | python3 - <<'PY'
import json, sys
try:
    data = json.load(sys.stdin)
    if isinstance(data, list) and data:
        first = data[0]
        room_id = first.get("id") or first.get("roomId")
        if room_id is not None:
            print(room_id)
except Exception:
    pass
PY
fi

if [ -n "$ROOM_ID" ]; then
    echo -e "${GREEN}✓ Using room ID $ROOM_ID for reservation tests${NC}"
else
    echo -e "${RED}✗ Could not detect a room ID. Create a room before running reservation tests.${NC}"
fi
echo ""

# Test 3: Create a reservation (only if we have a room ID)
RESERVATION_ID=""
if [ -n "$ROOM_ID" ]; then
    echo -e "${BLUE}[TEST 3] Creating a reservation...${NC}"
    START_TIME=$(date -u +"%Y-%m-%dT%H:%M:%S")
    REQUEST_BODY=$(cat <<EOF
{
  "roomId": $ROOM_ID,
  "employeeName": "Room Test User",
  "employeeEmail": "room.test@example.com",
  "department": "Operations",
  "purpose": "Automation Test",
  "startTime": "$START_TIME",
  "durationMinutes": 30
}
EOF
)
    CREATE_RESPONSE=$(curl -s -H "Content-Type: application/json" -d "$REQUEST_BODY" "$BASE_URL/api/reservations")
    echo "Reservation response: $CREATE_RESPONSE"
    RESERVATION_ID=$(parse_json_field "$CREATE_RESPONSE" "reservationId")
    if [ -n "$RESERVATION_ID" ]; then
        echo -e "${GREEN}✓ Reservation created with ID $RESERVATION_ID${NC}"
    else
        echo -e "${RED}✗ Reservation creation failed (check response above)${NC}"
    fi
    echo ""
fi

# Test 4: Get active reservations
echo -e "${BLUE}[TEST 4] Getting active reservations...${NC}"
ACTIVE_RESPONSE=$(curl -s "$BASE_URL/api/reservations/active")
echo "Active reservations: $ACTIVE_RESPONSE"
echo ""

# Test 5: Trigger reminder processing
echo -e "${BLUE}[TEST 5] Processing reminders...${NC}"
REMINDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/reservations/process-reminders")
echo "Reminder response: $REMINDER_RESPONSE"
echo ""

# Test 6: Trigger auto-release processing
echo -e "${BLUE}[TEST 6] Processing auto-releases...${NC}"
RELEASE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/reservations/process-auto-releases")
echo "Auto-release response: $RELEASE_RESPONSE"
echo ""

echo "=========================================="
echo "Test completed!"
echo "=========================================="
