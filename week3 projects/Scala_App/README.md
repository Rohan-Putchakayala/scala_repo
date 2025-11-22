# Visitor Management System

A comprehensive visitor management system built with Play Framework, Akka Actors, and Kafka. This system streamlines visitor check-in/check-out processes and automates notifications to relevant internal teams.

## Architecture

The system consists of two main components:

### 1. Main Application (Play Framework)
- **REST API**: Visitor check-in/check-out endpoints
- **Database**: Visitor and check-in record management using Slick
- **ID References**: Secure storage of ID numbers (no file uploads)
- **Email Service**: Direct notifications to host employees
- **Kafka Producer**: Publishes messages to notification queues
- **Akka Actors**: Orchestrates notification workflow

### 2. Messaging Microservice (Akka + Kafka)
- **IT Support Actor**: Handles WiFi access setup/teardown
- **Security Actor**: Manages security clearance and logging
- **Kafka Consumers**: Listens to notification topics

## Technology Stack

- **Framework**: Play Framework 2.9.1
- **Language**: Scala 2.13.13
- **Actor System**: Akka Typed 2.8.5
- **Message Queue**: Apache Kafka 3.6.1
- **Database**: H2 (optional development), Azure MySQL (production)
- **ORM**: Slick 5.3.0
- **Email**: JavaMail API

## Prerequisites

- JDK 11 or higher
- sbt 1.9.x
- Apache Kafka 3.6.x
- Azure MySQL (for production)

## Setup Instructions

### 1. Install Kafka

**On macOS:**
```bash
brew install kafka
brew services start zookeeper
brew services start kafka
```

**On Linux:**
```bash
# Download and extract Kafka
wget https://downloads.apache.org/kafka/3.6.1/kafka_2.13-3.6.1.tgz
tar -xzf kafka_2.13-3.6.1.tgz
cd kafka_2.13-3.6.1

# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties &

# Start Kafka
bin/kafka-server-start.sh config/server.properties &
```

### 2. Create Kafka Topics

```bash
# For macOS (using Homebrew installation)
kafka-topics --create --topic visitor-it-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic visitor-security-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic visitor-checkout-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# For manual installation
bin/kafka-topics.sh --create --topic visitor-it-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic visitor-security-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic visitor-checkout-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

### 3. Configure Email (Required for real emails)

Configure the Gmail account and App Password used by the EmailService.

1. **Update `conf/application.conf`:**
   - Set `email.smtp.user` and `email.from` to the **same Gmail address** (e.g. `my.company.app@gmail.com`)
   - Set `email.smtp.password` to the **Gmail App Password** for that account
   - **Important:** The **From address and SMTP user MUST be the same Gmail account**

2. (Optional) **Override via environment variables** instead of hard-coding:

```bash
export EMAIL_USER="your-gmail-account@gmail.com"        # must match email.from
export EMAIL_PASSWORD="your-gmail-app-password"         # Gmail App Password only
```

For Gmail, you MUST use a **Gmail App Password** — your normal account password will **NOT** work:
1. Go to Google Account settings
2. Enable 2-Factor Authentication
3. Generate an App Password for "Mail"
4. Use that App Password as `email.smtp.password` / `EMAIL_PASSWORD`

### 4. Build and Run

**Start the Main Application:**
```bash
sbt run
```

The API will be available at `http://localhost:9000`

**Start the Messaging Microservice (in a new terminal):**
```bash
sbt "messaging-service/run"
```

## API Endpoints

### Check-in a Visitor
```bash
POST /api/visitors/check-in
Content-Type: multipart/form-data

Fields:
- name: string (required)
- email: string (required)
- phoneNumber: string (required)
- company: string (optional)
- purposeOfVisit: string (required)
- hostEmployeeEmail: string (required)
- idProofNumber: string (required)
```

**Example using curl:**
```bash
curl -X POST http://localhost:9000/api/visitors/check-in \
  -F "name=John Doe" \
  -F "email=john.doe@example.com" \
  -F "phoneNumber=+1234567890" \
  -F "company=TechCorp" \
  -F "purposeOfVisit=Business Meeting" \
  -F "hostEmployeeEmail=host@company.com" \
  -F "idProofNumber=ID123456789"
```

### Check-out a Visitor
```bash
PUT /api/visitors/check-out/:checkInId
```

**Example:**
```bash
curl -X PUT http://localhost:9000/api/visitors/check-out/1
```

### Get Active Visitors
```bash
GET /api/visitors/active
```

**Example:**
```bash
curl http://localhost:9000/api/visitors/active
```

### Get Visitor History
```bash
GET /api/visitors/history/:email
```

**Example:**
```bash
curl http://localhost:9000/api/visitors/history/john.doe@example.com
```

### Get Visitor Details
```bash
GET /api/visitors/:id
```

**Example:**
```bash
curl http://localhost:9000/api/visitors/1
```

## Workflow

### Check-in Process
1. Reception staff captures visitor details and ID reference number
2. System creates visitor and check-in records
3. Notifications are sent:
   - **Host Employee**: Email notification of visitor arrival
   - **Visitor**: Email with WiFi credentials
   - **IT Support**: Kafka message to prepare network access
   - **Security Team**: Kafka message with visitor details and clearance status

### Check-out Process
1. Reception staff checks out visitor using check-in ID
2. System updates check-out time and status
3. Notifications are sent:
   - **IT Support**: Kafka message to revoke WiFi access
   - **Security Team**: Kafka message to close security clearance

## Notification Flow

```
┌─────────────────┐
│  Check-in API   │
└────────┬────────┘
         │
         ▼
┌────────────────────┐
│ NotificationActor  │
└─────┬─────┬────────┘
      │     │
      │     └──────────────────┐
      │                        │
      ▼                        ▼
┌──────────────┐      ┌──────────────┐
│ Email Service│      │ Kafka Producer│
└──────────────┘      └───────┬───────┘
                              │
                 ┌────────────┴────────────┐
                 │                         │
                 ▼                         ▼
        ┌─────────────────┐      ┌──────────────────┐
        │ IT Notifications│      │Security Notifs   │
        │     Topic       │      │    Topic         │
        └────────┬────────┘      └────────┬─────────┘
                 │                        │
                 ▼                        ▼
        ┌─────────────────┐      ┌──────────────────┐
        │ IT Support Actor│      │ Security Actor   │
        └─────────────────┘      └──────────────────┘
```

## Database Schema

### visitors
- id (BIGINT, PRIMARY KEY)
- name (VARCHAR)
- email (VARCHAR)
- phone_number (VARCHAR)
- company (VARCHAR, optional)
- purpose_of_visit (VARCHAR)
- host_employee_email (VARCHAR)
- id_proof_path (VARCHAR)
- created_at (TIMESTAMP)

### check_in_records
- id (BIGINT, PRIMARY KEY)
- visitor_id (BIGINT, FOREIGN KEY)
- check_in_time (TIMESTAMP)
- check_out_time (TIMESTAMP, optional)
- status (VARCHAR)
- notifications_sent (BOOLEAN)

## Configuration

Key configuration files:

- `conf/application.conf`: Main application settings
- `messaging-service/src/main/resources/application.conf`: Microservice settings
- `conf/routes`: API route definitions
- `conf/evolutions/default/1.sql`: Database schema

## Security Considerations

1. **ID Proof Storage**: Files are stored with unique identifiers in a secure directory
2. **File Type Validation**: Only jpg, jpeg, png, and pdf files are accepted
3. **Database**: Use PostgreSQL with proper authentication in production
4. **Email**: Use configuration/env variables for Gmail credentials (App Password only)
5. **Kafka**: Configure SSL/SASL for production deployments

## Production Deployment

1. **Switch to PostgreSQL**: Update `application.conf` database settings
2. **Configure proper secret key**: Set `APPLICATION_SECRET` environment variable
3. **Set up Kafka cluster**: Update bootstrap servers
4. **Enable HTTPS**: Configure SSL certificates
5. **Set up monitoring**: Integrate with logging and monitoring solutions

## Testing

```bash
# Run tests for main application
sbt test

# Run tests for messaging service
sbt "messaging-service/test"
```

## Troubleshooting

### Kafka Connection Issues
- Ensure Kafka and Zookeeper are running
- Check `kafka.bootstrap.servers` in configuration
- Verify topics are created

### Email Not Sending
- Verify `email.smtp.user`, `email.smtp.password`, and `email.from` in `conf/application.conf`
- Ensure `email.from` **exactly matches** `email.smtp.user` (same Gmail account)
- If using env vars, verify `EMAIL_USER` / `EMAIL_PASSWORD` are set and match the config
- For Gmail, ensure a **Gmail App Password** is used — normal Gmail password will **NOT** work
- Check firewall settings for port 587

### Database Issues
- For H2: Database is in-memory, data is lost on restart
- For PostgreSQL: Ensure database and user exist
- Check database connection string in `application.conf`

## License

This project is for educational purposes.

## Support

For issues or questions, please create an issue in the project repository.
