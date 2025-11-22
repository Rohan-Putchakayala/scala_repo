# Features Documentation

This document provides a comprehensive overview of all features implemented in the Visitor Management System.

## Core Features

### 1. Visitor Check-In ✅

**Endpoint:** `POST /api/visitors/check-in`

**Capabilities:**
- Capture visitor details (name, email, phone, company, purpose of visit)
- Capture and securely store ID reference numbers (text only)
- Record check-in timestamp
- Link visitor to host employee
- Generate unique visitor ID

**Technical Implementation:**
- Form-data handling (Postman-friendly)
- ID reference validation (required field)
- Database transaction management
- Automatic ID generation

### 2. Visitor Check-Out ✅

**Endpoint:** `PUT /api/visitors/check-out/:checkInId`

**Capabilities:**
- Record check-out timestamp
- Update visitor status
- Prevent double check-out
- Trigger cleanup notifications

**Technical Implementation:**
- Transactional updates
- Status validation
- Automated notification triggering

### 3. Automated Email Notifications ✅

**Email Types:**

#### Host Employee Notification
- **Trigger:** Visitor check-in
- **Content:** Visitor details, purpose of visit, check-in time
- **Format:** HTML email with formatted visitor information

#### IT WiFi Credentials
- **Trigger:** Visitor check-in
- **Content:** WiFi SSID and password for guest access
- **Format:** HTML email with styled credential box

#### Check-Out Confirmation
- **Trigger:** Visitor check-out
- **Content:** Thank you message with check-out time
- **Format:** HTML email

**Technical Implementation:**
- JavaMail API integration
- SMTP configuration
- HTML email templates
- Async email sending
- Error handling and logging

### 4. Kafka Message Queue Integration ✅

**Topics:**
- `visitor-it-notifications` - IT Support messages
- `visitor-security-notifications` - Security team messages
- `visitor-checkout-notifications` - Check-out notifications

**Features:**
- Reliable message delivery with acknowledgments
- Partitioned topics for scalability
- Idempotent producer configuration
- JSON message serialization
- Consumer group management

**Technical Implementation:**
- Kafka Producer Service in main app
- Kafka Consumer Service in messaging microservice
- Exactly-once semantics support
- Automatic retry mechanism

### 5. IT Support Integration ✅

**Capabilities:**
- Automatic WiFi access provisioning
- Guest network configuration
- Credential generation and delivery
- Access revocation on check-out
- Audit logging of IT operations

**Actor-Based Processing:**
- Asynchronous message handling
- Stateful session management
- Error recovery
- Real-time processing

### 6. Security Team Integration ✅

**Capabilities:**
- Visitor entry logging
- ID proof verification workflow
- Background check initiation
- Access badge management
- Security clearance tracking
- Exit logging

**Security Features:**
- Clearance status tracking
- ID document storage with access control
- Audit trail for all visitor movements
- Real-time security alerts

### 7. Actor-Based Architecture ✅

**Actors Implemented:**

#### NotificationActor (Main App)
- Orchestrates all notification workflows
- Coordinates email and Kafka messaging
- Handles check-in and check-out events
- Implements ask pattern for response handling

#### ITSupportActor (Messaging Service)
- Processes IT-related notifications
- Manages WiFi provisioning
- Handles network access tasks
- Logs IT operations

#### SecurityActor (Messaging Service)
- Processes security notifications
- Manages clearance workflows
- Handles badge provisioning
- Logs security events

**Benefits:**
- Concurrent message processing
- Fault isolation
- Scalable architecture
- Reactive system design

### 8. Database Management ✅

**Database Schema:**

#### Visitors Table
- Complete visitor profile
- Contact information
- Company affiliation
- Purpose of visit
- Host employee linkage
- ID proof storage reference
- Audit timestamps

#### Check-In Records Table
- Check-in/check-out timestamps
- Visitor status tracking
- Notification status
- Foreign key relationships
- Cascading deletes

**Features:**
- Automatic schema evolution
- H2 for development (in-memory)
- PostgreSQL ready for production
- Index optimization for queries
- Transaction management

**Technical Implementation:**
- Slick ORM
- Type-safe queries
- Async database operations
- Connection pooling

### 9. ID Reference Capture ✅

**Capabilities:**
- Capture alphanumeric ID reference numbers
- Validate presence for every visitor
- Persist IDs with visitor records
- Surface IDs to IT/Security notifications
- Audit-friendly history of all IDs

**Security Features:**
- Server-side validation (length/charset ready)
- Encrypted transport (HTTPS via Play)
- No file storage footprint
- Easy masking/redaction in UI layers

**Usage Examples:**
- Passport number
- Government ID number
- Badge ID / Access card number

### 10. RESTful API ✅

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/visitors/check-in` | Check in a visitor |
| PUT | `/api/visitors/check-out/:id` | Check out a visitor |
| GET | `/api/visitors/active` | List active visitors |
| GET | `/api/visitors/history/:email` | Get visitor history |
| GET | `/api/visitors/:id` | Get visitor details |

**Features:**
- RESTful design principles
- JSON request/response
- Comprehensive error handling
- HTTP status codes
- Input validation

### 11. Microservice Architecture ✅

**Components:**

#### Main Application (Play Framework)
- REST API
- Business logic
- Database operations
- Email service
- Kafka producer

#### Messaging Service (Standalone)
- Kafka consumer
- Actor system
- IT Support processing
- Security processing
- Independent deployment

**Benefits:**
- Separation of concerns
- Independent scaling
- Fault isolation
- Technology flexibility

### 12. Notification Auto-Stop ✅

**Feature:**
When a visitor checks out, the system automatically:
- Publishes check-out notifications to Kafka
- IT Support Actor revokes WiFi access
- Security Actor closes clearance
- Prevents duplicate notifications

**Technical Implementation:**
- Status-based notification filtering
- Idempotent message handling
- Actor coordination
- Event-driven architecture

## Advanced Features

### 13. Visitor History Tracking ✅

**Capabilities:**
- Complete visit history by email
- Check-in/check-out records
- Purpose of visit tracking
- Host employee records
- Time-series data

**Use Cases:**
- Repeat visitor identification
- Compliance reporting
- Visitor analytics
- Access pattern analysis

### 14. Active Visitor Dashboard ✅

**Capabilities:**
- Real-time list of checked-in visitors
- Visitor details display
- Check-in time tracking
- Host employee information
- Quick check-out access

**Use Cases:**
- Reception desk monitoring
- Security oversight
- Capacity management
- Emergency evacuation lists

### 15. Configuration Management ✅

**Configuration Files:**
- `application.conf` - Main app settings
- `messaging-service/application.conf` - Microservice settings
- Environment variable support
- Profile-based configuration

**Configurable Items:**
- Database connections
- Kafka bootstrap servers
- Email SMTP settings
- ID reference validation rules
- Actor system settings

### 16. Error Handling and Logging ✅

**Features:**
- Comprehensive exception handling
- Structured logging
- Actor supervision strategies
- Graceful degradation
- User-friendly error messages

**Logging Levels:**
- INFO: Normal operations
- WARN: Recoverable issues
- ERROR: Failures and exceptions

## Technical Highlights

### Performance Optimizations
- Async/non-blocking operations
- Database connection pooling
- Kafka batching
- Actor-based concurrency
- Indexed database queries

### Scalability Features
- Horizontal scaling ready
- Stateless API design
- Kafka partitioning
- Actor system distribution
- Database replication support

### Security Measures
- File type validation
- Secure file storage
- Environment variable secrets
- Input sanitization
- SQL injection prevention (Slick)

### Maintainability
- Clear separation of concerns
- Dependency injection
- Type-safe code (Scala)
- Modular architecture
- Comprehensive documentation

## Future Enhancement Possibilities

### Short-term
- Web UI/Admin dashboard
- Visitor pre-registration
- QR code generation
- Mobile app for visitors
- SMS notifications

### Medium-term
- Facial recognition integration
- Badge printing integration
- Parking space management
- Meeting room booking
- Visitor analytics dashboard

### Long-term
- AI-based risk assessment
- Integration with building management
- Multi-location support
- Advanced reporting
- Compliance automation

## System Requirements Met

✅ **Requirement 1:** Visitor Check-In and Check-Out  
✅ **Requirement 2:** ID Reference Capture and Storage  
✅ **Requirement 3:** Host Employee Email Notifications  
✅ **Requirement 4:** IT Support WiFi Setup  
✅ **Requirement 5:** Security Team Notifications  
✅ **Requirement 6:** Automated Notifications Stop on Check-out  
✅ **Requirement 7:** Play Framework REST API  
✅ **Requirement 8:** Akka Actors for Messaging  
✅ **Requirement 9:** Kafka Message Queue  
✅ **Requirement 10:** Microservice Architecture  

## Technology Stack Summary

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Play Framework | 2.9.1 |
| Language | Scala | 2.13.13 |
| Actor System | Akka Typed | 2.8.5 |
| Message Queue | Apache Kafka | 3.6.1 |
| Database (Dev) | H2 | 2.2.224 |
| Database (Prod) | PostgreSQL | 42.7.1 |
| ORM | Slick | 5.3.0 |
| Email | JavaMail | 1.6.2 |
| Build Tool | sbt | Latest |

---

**Note:** This system is production-ready with proper configuration of database, Kafka cluster, and email service in a production environment.
