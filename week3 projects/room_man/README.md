# Room Management Microservices System

A distributed room reservation system built with **Scala**, **Play Framework**, **Akka**, and **Apache Kafka** using a true microservices architecture with two independent services.

## 🏗️ Architecture Overview

This system consists of **two completely separate microservice projects** communicating via Kafka:

```
room-management-service/     notification-service/
├── build.sbt               ├── build.sbt
├── project/                ├── project/
├── app/                    ├── src/main/scala/
└── conf/                   └── src/main/resources/
```

### 1. Room Management Service (Play Framework + Kafka Producer)
- **Technology**: Play Framework 2.9.1, Scala 2.13
- **Port**: 9000
- **Location**: `room-management-service/`
- **Responsibilities**:
  - REST API for room reservations
  - Room availability checking
  - Database operations (MySQL)
  - Publishing events to Kafka topics
  - Automated reminder and auto-release processing

### 2. Notification Service (Akka + Kafka Consumer)
- **Technology**: Akka Typed 2.8.5, Scala 2.13
- **Location**: `notification-service/`
- **Responsibilities**:
  - Consuming Kafka messages
  - Processing notifications via Akka actors
  - Sending email notifications
  - Administrative event logging

## 🚀 Quick Start

### Prerequisites

- **Java 11+**
- **Scala 2.13+**
- **SBT 1.8+**
- **Docker & Docker Compose**

### Start All Services (Recommended)

```bash
# Navigate to project root
cd "Scala_App 2"

# Start infrastructure and both microservices
./start-microservices.sh
```

This script will:
1. Start infrastructure (Kafka, MySQL, Kafka UI)
2. Create Kafka topics
3. Build and start Room Management Service
4. Build and start Notification Service
5. Provide monitoring and graceful shutdown

### Manual Development Mode

```bash
# Terminal 1: Start infrastructure
cd "Scala_App 2"
docker-compose up -d

# Terminal 2: Room Management Service
cd room-management-service
sbt run

# Terminal 3: Notification Service  
cd notification-service
sbt run
```

## 📊 Service Details

### Room Management Service (Play + Kafka Producer)

**Location**: `room-management-service/`
**Port**: 9000

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/reservations` | Create new reservation |
| `GET` | `/api/reservations/active` | List active reservations |
| `GET` | `/api/reservations/availability` | Check room availability |
| `GET` | `/api/rooms` | List all rooms |
| `POST` | `/api/reservations/process-reminders` | Process pending reminders |
| `POST` | `/api/reservations/process-auto-releases` | Process auto-releases |
| `GET` | `/health` | Service health check |

#### Example API Calls

**Create Reservation:**
```bash
curl -X POST http://localhost:9000/api/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": 1,
    "employeeName": "John Doe",
    "employeeEmail": "john.doe@company.com",
    "department": "Engineering",
    "purpose": "Team Standup",
    "startTime": "2024-02-15T14:00:00",
    "durationMinutes": 60
  }'
```

**Check Availability:**
```bash
curl "http://localhost:9000/api/reservations/availability?roomId=1&startTime=2024-02-15T14:00:00&endTime=2024-02-15T15:00:00"
```

### Notification Service (Akka + Kafka Consumer)

**Location**: `notification-service/`
**No HTTP endpoints** - Pure Kafka consumer with Akka actors

- Consumes messages from Kafka topics
- Processes notifications via typed actors
- Sends emails for reminders and confirmations
- Logs administrative events

## 🔄 Kafka Message Flow

### Topics and Message Types

| Topic | Producer | Consumer | Message Type |
|-------|----------|----------|--------------|
| `room-preparation-notifications` | Room Service | Notification Service | Room setup alerts |
| `reservation-reminder-notifications` | Room Service | Notification Service | Meeting reminders |
| `room-release-notifications` | Room Service | Notification Service | Auto-release notifications |
| `admin-notifications` | Room Service | Notification Service | Administrative events |

### Communication Flow

```
Room Management Service                    Notification Service
         |                                        |
    [Create Reservation] ──────────────────────> [Email Confirmation]
         |                                        |
    [Publish to Kafka] ───────Kafka───────────> [Akka Actor Processing]
         |                                        |
    [Room Prep Event] ────────────────────────> [Facilities Alert]
         |                                        |
    [Reminder Event] ─────────────────────────> [Email Reminder]
         |                                        |
    [Auto-Release] ───────────────────────────> [Release Email]
```

## 🛠️ Project Structure

### Room Management Service
```
room-management-service/
├── build.sbt                      # Play Framework dependencies
├── project/plugins.sbt            # Play plugin
├── app/
│   ├── controllers/
│   │   └── ReservationController.scala
│   ├── models/
│   │   ├── Models.scala
│   │   ├── KafkaModels.scala
│   │   ├── DateTimeSupport.scala
│   │   └── RoomTables.scala
│   ├── services/
│   │   ├── ReservationService.scala
│   │   └── KafkaProducerService.scala
│   └── repositories/
│       └── ReservationRepository.scala
└── conf/
    ├── application.conf
    └── routes
```

### Notification Service
```
notification-service/
├── build.sbt                      # Akka dependencies + assembly
├── project/plugins.sbt            # Assembly plugin
└── src/main/
    ├── scala/
    │   ├── NotificationServiceApp.scala
    │   ├── actors/
    │   │   └── NotificationProcessorActor.scala
    │   ├── services/
    │   │   ├── EmailService.scala
    │   │   └── KafkaConsumerService.scala
    │   └── models/
    │       └── NotificationModels.scala
    └── resources/
        └── application.conf
```

## 🧪 Testing

### Test the API
```bash
./test-api.sh
```

### Monitor Kafka Messages
```bash
# Access Kafka UI
open http://localhost:8080

# Monitor specific topic
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic room-preparation-notifications \
  --from-beginning
```

### Service Health Checks
```bash
# Room Management Service
curl http://localhost:9000/health

# Check if services are running
ps aux | grep -E "(sbt|java.*notification)"
```

## 📦 Build and Deployment

### Build Individual Services

```bash
# Room Management Service
cd room-management-service
sbt compile
sbt dist

# Notification Service
cd notification-service
sbt compile
sbt assembly
```

### Run Services Independently

```bash
# Start just Room Management Service
cd room-management-service
sbt run

# Start just Notification Service (after building assembly)
cd notification-service
java -jar target/scala-2.13/notification-service-assembly.jar
```

## ⚙️ Configuration

### Room Management Service
**File**: `room-management-service/conf/application.conf`

```hocon
# Database
slick.dbs.default.db.url = "jdbc:mysql://localhost:3306/room_management"
slick.dbs.default.db.user = "roomuser"
slick.dbs.default.db.password = "roompassword"

# Kafka Producer
kafka.bootstrap.servers = "localhost:9092"
kafka.enabled = true

# Business Rules
room.management.reminder.advance.minutes = 15
room.management.auto.release.delay.minutes = 15
```

### Notification Service
**File**: `notification-service/src/main/resources/application.conf`

```hocon
# Kafka Consumer
kafka.bootstrap.servers = "localhost:9092"
kafka.consumer.group.id = "notification-service-group"

# Email Configuration
email.smtp.host = "smtp.gmail.com"
email.smtp.port = 587
email.smtp.user = ${?EMAIL_USER}
email.smtp.password = ${?EMAIL_PASSWORD}

# Akka Configuration
akka.loglevel = "INFO"
```

## 🔧 Key Features

### Room Management Service Features:
- ✅ **RESTful API** with comprehensive endpoints
- ✅ **Database Integration** with MySQL via Slick
- ✅ **Kafka Producer** with idempotent configuration
- ✅ **Business Logic** for reservations and availability
- ✅ **Automated Processing** for reminders and releases
- ✅ **Health Checks** and monitoring endpoints

### Notification Service Features:
- ✅ **Akka Typed Actors** for message processing
- ✅ **Kafka Consumer** with resilient streaming
- ✅ **Email Service** integration with Gmail SMTP
- ✅ **Error Handling** and retry logic
- ✅ **Backpressure Management** via Akka Streams

## 🚨 Troubleshooting

### Common Issues

**1. Services Won't Start**
```bash
# Check if ports are available
netstat -tulpn | grep -E ":9000|:9092"

# Check SBT version
sbt sbtVersion

# Verify Java version
java -version
```

**2. Kafka Connection Issues**
```bash
# Verify Kafka is running
docker ps | grep kafka

# Check topics exist
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

**3. Database Connection Issues**
```bash
# Check MySQL status
docker logs mysql-db

# Test database connection
mysql -h localhost -u roomuser -p room_management
```

**4. Service Logs**
```bash
# Room Management Service logs (in sbt console)
# Check for Play application startup messages

# Notification Service logs (console output)
# Check for Akka system startup and Kafka consumer messages
```

## 🔒 Production Considerations

- **API Security**: Implement authentication and authorization for Room Management Service
- **Database**: Use connection encryption and proper credentials management
- **Kafka**: Enable SASL/SSL for production clusters
- **Email**: Use proper SMTP credentials and rate limiting
- **Monitoring**: Add metrics collection and alerting
- **Scaling**: Both services can be scaled horizontally

## 🤝 Development Workflow

### Adding New Features

**To Room Management Service:**
1. `cd room-management-service`
2. Add new endpoints in `controllers/`
3. Add business logic in `services/`
4. Add new Kafka events if needed
5. Test with `sbt run`

**To Notification Service:**
1. `cd notification-service`
2. Add new message types in `models/`
3. Update actor behavior in `actors/`
4. Handle new Kafka topics in `services/`
5. Test with `sbt run`

### Inter-Service Communication
- All communication happens via Kafka topics
- Services are completely independent
- No direct HTTP calls between services
- Each service has its own database/storage (if needed)

## 📄 License

This project is licensed under the MIT License.

## 🆘 Support

For issues and questions:
- Create GitHub issues for bugs and feature requests
- Check service logs for detailed error information
- Monitor Kafka topics for message flow issues

---

**Built with ❤️ using Scala microservices architecture**