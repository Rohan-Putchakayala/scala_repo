# Office Meeting Room Management System

A comprehensive meeting room management system built with Play Framework, Akka Actors, and Kafka. This system streamlines room reservations and automates notifications to relevant internal teams.

## Architecture

The system consists of two main components:

### 1. Main Application (Play Framework)
- **REST API**: Room reservation and availability endpoints
- **Database**: Room and reservation management using Slick
- **Email Service**: Notifications to employees about bookings, reminders, and releases
- **Kafka Producer**: Publishes room-related notifications to Kafka topics
- **Akka Actors**: Orchestrates reservation notification workflow

### 2. Messaging Microservice (Akka + Kafka)
- **Room Service Actor**: Handles room preparation and cleanup
- **Admin Actor**: Manages admin notifications and monitoring
- **Kafka Consumers**: Listen to room management notification topics

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
wget https:
tar -xzf kafka_2.13-3.6.1.tgz
cd kafka_2.13-3.6.1

# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties &

# Start Kafka
bin/kafka-server-start.sh config/server.properties &
```

### 2. Kafka Topics

The room management service uses the following topics (created automatically on first use):

- `room-preparation-notifications`
- `room-release-notifications`
- `admin-notifications`

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

The API will be available at `http:

**Start the Messaging Microservice (in a new terminal):**
```bash
sbt "messaging-service/run"
```

## API Endpoints

### Create a Reservation
```bash
POST /api/reservations
Content-Type: application/json

Body:
{
  "roomId": 1,
  "employeeName": "John Doe",
  "employeeEmail": "john.doe@example.com",
  "department": "Engineering",
  "purpose": "Project Meeting",
  "startTime": "2024-01-15T10:00:00",
  "durationMinutes": 60
}
```

### Check Room Availability
```bash
GET /api/reservations/availability?roomId=1&startTime=2024-01-15T10:00:00&endTime=2024-01-15T11:00:00
```

### Get Active Reservations
```bash
GET /api/reservations/active
```

### Get Rooms
```bash
GET /api/rooms
```

## Workflow

### Reservation Process
1. Admin staff captures reservation details and requested time window
2. System ensures the room is free and creates a reservation record
3. Notifications are sent:
   - **Employee**: Email confirmation and pre-meeting reminder
   - **Room Service Team**: Kafka message to prepare the room
   - **Admins**: Kafka admin notification for monitoring

### Auto-Release Process
1. Scheduler checks reservations that have not started within the grace period
2. System auto-releases the room and updates reservation status
3. Notifications are sent:
   - **Employee**: Email about auto-release
   - **Room Service Team**: Kafka message to reset the room
   - **Admins**: Kafka admin notification

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

### rooms
- id (BIGINT, PRIMARY KEY)
- name (VARCHAR)
- capacity (INT)
- location (VARCHAR)
- amenities (TEXT, optional)
- is_active (BOOLEAN)
- created_at (TIMESTAMP)

### reservations
- id (BIGINT, PRIMARY KEY)
- room_id (BIGINT, FOREIGN KEY)
- employee_name (VARCHAR)
- employee_email (VARCHAR)
- department (VARCHAR)
- purpose (VARCHAR)
- start_time (TIMESTAMP)
- end_time (TIMESTAMP)
- status (VARCHAR)
- notifications_sent (BOOLEAN)
- reminder_sent (BOOLEAN)

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
