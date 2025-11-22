# Postman & API Testing Guide

## 📥 Import Postman Collection

1. Open Postman
2. Click **Import** button (top left)
3. Select **File** tab
4. Choose: `Visitor-Management.postman_collection.json`
5. Click **Import**

All 5 endpoints will be ready to use!

---

## 🚀 Quick Test Commands (cURL)

### 1. Check-In Visitor

```bash
curl -X POST http://localhost:9000/api/visitors/check-in \
  -F "name=John Doe" \
  -F "email=john.doe@example.com" \
  -F "phoneNumber=+1-555-123-4567" \
  -F "company=Acme Corp" \
  -F "purposeOfVisit=Business Meeting" \
  -F "hostEmployeeEmail=host@company.com" \
  -F "idProofNumber=ID123456789"
```

**Replace `ID123456789` with the actual ID number shown on the visitor’s document**

### 2. Check-Out Visitor

```bash
curl -X PUT http://localhost:9000/api/visitors/check-out/1
```

**Replace `1` with actual checkInId from check-in response**

### 3. Get Active Visitors

```bash
curl http://localhost:9000/api/visitors/active
```

### 4. Get Visitor History

```bash
curl http://localhost:9000/api/visitors/history/john.doe@example.com
```

### 5. Get Visitor by ID

```bash
curl http://localhost:9000/api/visitors/1
```

---

## 📋 Postman Manual Setup (If Import Doesn't Work)

### Check-In Endpoint

**Method:** `POST`  
**URL:** `http://localhost:9000/api/visitors/check-in`

**Body Tab:**
- Select: `form-data`
- Add fields:

| Key | Type | Value |
|-----|------|-------|
| name | Text | John Doe |
| email | Text | john.doe@example.com |
| phoneNumber | Text | +1-555-123-4567 |
| company | Text | Acme Corp |
| purposeOfVisit | Text | Business Meeting |
| hostEmployeeEmail | Text | host@company.com |
| idProofNumber | Text | ID123456789 |

**Important:** `idProofNumber` is a free-text field (no file upload needed). Use any government ID, badge number, etc.

---

## ✅ Expected Responses

### Check-In Success (200 OK)
```json
{
  "visitorId": 1,
  "checkInId": 1,
  "checkInTime": "2025-11-18T16:30:00",
  "message": "Visitor checked in successfully. Notifications sent."
}
```

### Check-Out Success (200 OK)
```json
{
  "checkInId": 1,
  "checkOutTime": "2025-11-18T17:45:00",
  "message": "Visitor checked out successfully"
}
```

### Active Visitors (200 OK)
```json
[
  {
    "checkInId": 1,
    "visitorId": 1,
    "visitorName": "John Doe",
    "visitorEmail": "john.doe@example.com",
    "phoneNumber": "+1-555-123-4567",
    "company": "Acme Corp",
    "purposeOfVisit": "Business Meeting",
    "hostEmployeeEmail": "host@company.com",
    "checkInTime": "2025-11-18T16:30:00",
    "status": "CHECKED_IN"
  }
]
```

---

## 🧪 Test Workflow

1. **Check-In:** POST `/api/visitors/check-in` with the textual ID number
2. **Get Active:** GET `/api/visitors/active` - should show your visitor
3. **Check-Out:** PUT `/api/visitors/check-out/{checkInId}` - use checkInId from step 1
4. **Get History:** GET `/api/visitors/history/{email}` - should show check-in/out records
5. **Get Visitor:** GET `/api/visitors/{visitorId}` - get visitor details

---

## ⚠️ Common Errors

| Error | Solution |
|-------|----------|
| "Missing boundary header" | Use `form-data` in Body tab, not `x-www-form-urlencoded` |
| "Missing required fields" | Ensure all fields are filled (name, email, phoneNumber, purposeOfVisit, hostEmployeeEmail, idProofNumber) |
| "Invalid value for idProofNumber" | Provide alphanumeric text only (no files) |
| "Connection refused" | Make sure Play app is running on port 9000 |

---

**Happy Testing! 🎉**

