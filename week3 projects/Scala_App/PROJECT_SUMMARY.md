# Visitor Management System - Project Summary

## 🎯 Project Overview

A production-ready corporate Visitor Management System built with Play Framework, Akka Actors, and Apache Kafka. The system streamlines visitor check-in/check-out processes and automates notifications to relevant internal teams (Host Employees, IT Support, Security).

## 📋 System Requirements (All Implemented ✅)

1. ✅ **Visitor Check-In and Check-Out**
   - Capture visitor details and ID reference numbers during check-in
   - Secure storage of visitor records and ID references
   - Automated timestamp recording

2. ✅ **Automated Notifications**
   - Host Employee: Email notification of visitor arrival
   - IT Support: WiFi access credentials via email + Kafka message
   - Security Team: Visitor entry notification via Kafka
   - Automatic notification stop after check-out

3. ✅ **Backend and Deployment**
   - Play Framework REST API for check-in/check-out operations
   - Separate microservice using Akka Actors for IT/Security messaging
   - Kafka message queue for reliable communication

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MAIN APPLICATION                         │
│                   (Play Framework)                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐  ┌──────────────┐                      │
│  │ REST API     │  │  Visitor     │                      │
│  │ Controller   │→→│  Service     │                      │
│  └──────────────┘  └──────────────┘                      │
│         ↓                 ↓                               │
│  ┌──────────────┐  ┌──────────────┐                      │
│  │  Database    │  │ Notification │                      │
│  │ (Slick/H2)   │  │   Actor      │                      │
│  └──────────────┘  └──────┬───────┘                      │
│                            ↓                               │
│                    ┌───────────────┐                      │
│                    │ Email Service │                      │
│                    │ Kafka Producer│                      │
│                    └───────┬───────┘                      │
└────────────────────────────┼──────────────────────────────┘
                             ↓
                    ┌────────────────┐
                    │  Apache Kafka  │
                    │   (3 Topics)   │
                    └────────┬───────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│              MESSAGING MICROSERVICE                         │
│                  (Akka Actors)                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────┐        ┌──────────────────┐         │
│  │ Kafka Consumer   │───────→│  IT Support      │         │
│  │    Service       │        │     Actor        │         │
│  └──────────────────┘        └──────────────────┘         │
│           ↓                                                 │
│  ┌──────────────────┐                                      │
│  │   Security       │                                      │
│  │     Actor        │                                      │
│  └──────────────────┘                                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 📁 Project Structure

```
Scala_App/
├── app/                                  # Main Play application
│   ├── actors/
│   │   └── NotificationActor.scala      # Orchestrates notifications
│   ├── controllers/
│   │   └── VisitorController.scala      # REST API endpoints
│   ├── models/
│   │   ├── Visitor.scala                # Domain models
│   │   ├── NotificationMessage.scala    # Notification types
│   │   └── Tables.scala                 # Slick database schema
│   ├── modules/
│   │   └── ActorModule.scala            # Dependency injection
│   ├── repositories/
│   │   └── VisitorRepository.scala      # Database operations
│   └── services/
│       ├── EmailService.scala           # Email notifications
│       ├── KafkaProducerService.scala   # Kafka messaging
│       └── VisitorService.scala         # Business logic
│
├── conf/
│   ├── application.conf                 # Main configuration
│   ├── routes                           # API routes
│   └── evolutions/
│       └── default/
│           └── 1.sql                    # Database schema
│
├── messaging-service/                   # Separate microservice
│   └── src/main/scala/
│       ├── actors/
│       │   ├── ITSupportActor.scala     # IT operations
│       │   └── SecurityActor.scala      # Security operations
│       ├── models/
│       │   └── NotificationModels.scala # Message models
│       ├── services/
│       │   └── KafkaConsumerService.scala # Kafka consumer
│       └── MessagingServiceApp.scala    # Main entry point
│
├── project/
│   ├── build.properties
│   └── plugins.sbt                      # Play plugin
│
├── build.sbt                            # SBT build configuration
├── README.md                            # Comprehensive documentation
├── QUICKSTART.md                        # Quick start guide
├── FEATURES.md                          # Features documentation
├── test-api.sh                          # API test script
└── .gitignore                           # Git ignore rules
```

## 🛠️ Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Framework** | Play Framework 2.9.1 | Web application framework |
| **Language** | Scala 2.13.13 | Primary programming language |
| **Concurrency** | Akka Typed 2.8.5 | Actor-based concurrency |
| **Messaging** | Apache Kafka 3.6.1 | Message queue |
| **Database** | Azure MySQL / (H2 optional) | Data persistence |
| **ORM** | Slick 5.3.0 | Database access |
| **Email** | JavaMail 1.6.2 | Email notifications |
| **Build Tool** | sbt | Build and dependency management |

## 🚀 Key Features

### 1. Visitor Management
- Complete check-in/check-out workflow
- ID reference capture with validation
- Visitor history tracking
- Active visitors dashboard

### 2. Automated Notifications
- **Host Employee**: Email on visitor arrival
- **Visitor**: WiFi credentials via email
- **IT Support**: Kafka message + WiFi setup
- **Security Team**: Kafka message + clearance workflow
- **Auto-stop**: Notifications cease on check-out

### 3. Microservice Architecture
- **Main App**: REST API, database, email
- **Messaging Service**: Independent Akka-based service
- **Kafka**: Decoupled communication
- **Scalability**: Services can scale independently

### 4. Actor-Based Processing
- **NotificationActor**: Orchestrates all notifications
- **ITSupportActor**: Handles IT operations
- **SecurityActor**: Manages security workflows
- **Benefits**: Concurrent, fault-tolerant, reactive

### 5. Security Features
- ID reference validation
- Audit-friendly ID number storage
- Environment-based secrets
- Input sanitization
- Type-safe database queries

## 📊 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/visitors/check-in` | Check in a visitor (multipart form) |
| PUT | `/api/visitors/check-out/:id` | Check out a visitor |
| GET | `/api/visitors/active` | Get all checked-in visitors |
| GET | `/api/visitors/history/:email` | Get visitor history |
| GET | `/api/visitors/:id` | Get visitor details |

## 🗄️ Database Schema

### visitors
- id (PK, auto-increment)
- name, email, phone_number
- company (optional)
- purpose_of_visit
- host_employee_email
- id_proof_path
- created_at

### check_in_records
- id (PK, auto-increment)
- visitor_id (FK → visitors.id)
- check_in_time
- check_out_time (optional)
- status (CHECKED_IN / CHECKED_OUT)
- notifications_sent

## 📨 Kafka Topics

1. **visitor-it-notifications**
   - IT Support messages
   - WiFi access requests
   - Network configuration

2. **visitor-security-notifications**
   - Security clearance requests
   - ID verification
   - Badge generation

3. **visitor-checkout-notifications**
   - Check-out events
   - Access revocation
   - Cleanup operations

## 🔧 Configuration

### Main Application (conf/application.conf)
- Database: Azure MySQL (primary) / H2 (optional local override)
- Kafka: localhost:9092 (configurable)
- Email: SMTP settings via environment variables

### Messaging Service (messaging-service/.../application.conf)
- Kafka bootstrap servers
- Consumer group: `visitor-messaging-service`
- Akka actor system configuration

## 🧪 Testing

### Automated Test Script
```bash
./test-api.sh
```

### Manual Testing
```bash
# Check in a visitor
curl -X POST http://localhost:9000/api/visitors/check-in \
  -F "name=John Doe" \
  -F "email=john@example.com" \
  -F "phoneNumber=+1234567890" \
  -F "purposeOfVisit=Meeting" \
  -F "hostEmployeeEmail=host@company.com" \
  -F "idProofNumber=ID123456789"

# Get active visitors
curl http://localhost:9000/api/visitors/active

# Check out visitor
curl -X PUT http://localhost:9000/api/visitors/check-out/1
```

## 🚀 Deployment

### Prerequisites
1. JDK 11+
2. sbt 1.9+
3. Apache Kafka 3.6+
4. PostgreSQL (production)

### Steps
1. Install and start Kafka + Zookeeper
2. Create Kafka topics
3. Set environment variables (EMAIL_USER, EMAIL_PASSWORD)
4. Run main application: `sbt run`
5. Run messaging service: `sbt "messaging-service/run"`

### Production Considerations
- Switch to PostgreSQL database
- Configure Kafka cluster
- Set up proper secret management
- Enable HTTPS/SSL
- Configure monitoring and logging
- Set up CI/CD pipeline

## 📈 Scalability

### Horizontal Scaling
- **Main App**: Stateless design, can run multiple instances
- **Messaging Service**: Multiple consumer instances
- **Kafka**: Partitioned topics for parallel processing
- **Database**: Read replicas, connection pooling

### Performance
- Async/non-blocking operations
- Actor-based concurrency
- Database query optimization with indexes
- Kafka message batching

## 🔒 Security

- ID reference validation and secure storage
- Environment-based secrets (no hardcoded credentials)
- SQL injection prevention (Slick type-safe queries)
- Input validation on all endpoints
- Least-privilege database credentials

## 📚 Documentation

- **README.md**: Comprehensive system documentation
- **QUICKSTART.md**: 5-minute setup guide
- **FEATURES.md**: Detailed feature list
- **PROJECT_SUMMARY.md**: This file - high-level overview
- **Inline comments**: Code documentation

## 🎯 Project Status

✅ **Complete and Production-Ready**

All system requirements have been implemented:
- ✅ Visitor check-in/check-out with ID reference capture
- ✅ Automated email notifications
- ✅ IT Support integration via Kafka
- ✅ Security Team integration via Kafka
- ✅ Play Framework REST API
- ✅ Akka Actors microservice
- ✅ Kafka message queue
- ✅ Notification auto-stop on check-out

## 👥 Usage Scenarios

### Reception Staff
1. Visitor arrives at reception
2. Staff opens check-in form
3. Enter visitor details + ID reference number
4. System automatically notifies all parties
5. Visitor receives WiFi credentials
6. When leaving, staff checks out visitor

### IT Support
- Automatically receives Kafka messages
- WiFi credentials prepared and sent
- On check-out, access automatically revoked

### Security Team
- Real-time notifications of visitor entries
- ID reference available for verification
- Clearance status tracked
- Exit notifications received

### Host Employees
- Immediate email when visitor arrives
- Visitor details and check-in time
- Can prepare for the meeting

## 🔄 Future Enhancements

### Immediate
- Web UI for reception staff
- Visitor pre-registration portal
- QR code-based check-in

### Future
- Mobile app for visitors
- SMS notifications
- Badge printing integration
- Analytics dashboard
- Multi-location support

---

**Created by:** Developer using Play Framework, Akka, and Kafka  
**License:** Educational/Internal Use  
**Version:** 1.0.0  
**Last Updated:** November 2025
