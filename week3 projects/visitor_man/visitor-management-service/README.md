# Visitor Management Service

A combined microservice that handles visitor reception validation and processing with direct Kafka event publishing.

## Overview

This service combines the functionality of the previous `visitor-reception-service` and `visitor-processing-service` into a single, streamlined microservice. It provides:

- **Reception Validation**: Validates incoming requests from the receptionist UI
- **Visitor Processing**: Handles visitor check-in/checkout business logic
- **Direct Kafka Publishing**: Publishes events directly to Kafka (no outbox pattern)
- **Authentication**: JWT-based authentication for API endpoints
- **Database Management**: MySQL database operations with Slick ORM

## Key Changes from Previous Architecture

### 1. **Service Consolidation**
- Merged reception validation and processing logic into a single service
- Eliminated network calls between reception and processing services
- Reduced latency and complexity

### 2. **Direct Kafka Publishing**
- Removed outbox pattern and OutboxPoller
- Events are published directly to Kafka during API operations
- Improved real-time event delivery
- Simplified error handling and monitoring

### 3. **Enhanced Validation**
- Comprehensive input validation at the controller level
- Better error messages with specific error codes
- Improved logging for debugging and monitoring

## API Endpoints

### Visitor Management
- `POST /api/visitors/checkin` - Check-in a visitor
- `POST /api/visitors/checkout/:id` - Check-out a visitor by visit ID
- `GET /api/visitors/:id` - Get visitor details and visit history
- `GET /api/visits/active` - Get all active visits

### Authentication
- `POST /auth/login` - Authenticate user and get JWT token

### Health Check
- `GET /health` - Service health check

## Event Publishing

The service publishes the following events directly to Kafka:

### Check-in Event
```json
{
  "eventType": "visitor.checkin",
  "visitId": 123,
  "visitorId": 456,
  "hostEmployeeId": 789,
  "hostName": "John Doe",
  "hostEmail": "john.doe@company.com",
  "purpose": "Business Meeting",
  "visitor": {
    "firstName": "Jane",
    "lastName": "Smith",
    "email": "jane.smith@example.com",
    "phone": "+1234567890"
  },
  "idProofHash": "sha256_hash_of_aadhaar",
  "checkinDate": "2024-01-15",
  "correlationId": "uuid",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Check-out Event
```json
{
  "eventType": "visitor.checkout",
  "visitId": 123,
  "checkoutDate": "2024-01-15",
  "correlationId": "uuid",
  "timestamp": "2024-01-15T15:45:00Z"
}
```

## Configuration

### Application Configuration (`application.conf`)

```hocon
# Database
slick.dbs.default.profile = "slick.jdbc.MySQLProfile$"
slick.dbs.default.db.driver = "com.mysql.cj.jdbc.Driver"
slick.dbs.default.db.url = "jdbc:mysql://localhost:3306/visitor_management"
slick.dbs.default.db.user = "your_username"
slick.dbs.default.db.password = "your_password"

# Kafka Configuration
kafka.bootstrap.servers = "localhost:9092"
kafka.topic.checkin = "visitor.checkin"
kafka.topic.checkout = "visitor.checkout"

# JWT Configuration
jwt.secret = "your-jwt-secret"
jwt.expiration.hours = 24
```

### Environment Variables

```bash
# Database
DATABASE_URL=jdbc:mysql://localhost:3306/visitor_management
DATABASE_USER=your_username
DATABASE_PASSWORD=your_password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_CHECKIN_TOPIC=visitor.checkin
KAFKA_CHECKOUT_TOPIC=visitor.checkout

# JWT
JWT_SECRET=your-jwt-secret
```

## Database Schema

The service uses the following main tables:

- `visitors` - Visitor information
- `visits` - Visit records with check-in/check-out times
- `employees` - Host employee information
- `id_proofs` - Hashed ID proof information

Note: The `outbox_events` table has been removed as we now publish directly to Kafka.

## Dependencies

### Core Dependencies
- Play Framework 2.9
- Slick 3.4 (Database ORM)
- MySQL Connector
- Kafka Client 3.7.0
- Akka/Pekko (for concurrent operations)

### Authentication & Security
- JWT Scala
- BCrypt for password hashing

### Testing
- ScalaTest
- Play Test

## Development Setup

### Prerequisites
- Java 11 or higher
- SBT 1.8+
- MySQL 8.0+
- Apache Kafka 3.7+

### Running Locally

1. **Start MySQL and Kafka**
   ```bash
   # Start MySQL
   brew services start mysql
   
   # Start Kafka (with Zookeeper)
   bin/zookeeper-server-start.sh config/zookeeper.properties
   bin/kafka-server-start.sh config/server.properties
   ```

2. **Create Database**
   ```sql
   CREATE DATABASE visitor_management;
   ```

3. **Configure Application**
   ```bash
   cp conf/application.conf.example conf/application.conf
   # Edit configuration with your database and Kafka settings
   ```

4. **Run Application**
   ```bash
   sbt run
   ```

The service will start on `http://localhost:9000`

### Building for Production

```bash
# Create distribution package
sbt dist

# Build Docker image
sbt docker:publishLocal

# Run with Docker
docker run -p 9000:9000 visitor-management-service:1.0-SNAPSHOT
```

## Monitoring and Logging

### Logging
- Structured logging with Logback
- Request/response logging for all API calls
- Kafka publishing success/failure logging
- Database operation logging

### Metrics
- Service health checks via `/health` endpoint
- Kafka producer metrics available through JMX
- Database connection pool metrics

## Error Handling

The service provides comprehensive error handling with specific error codes:

### Validation Errors
- `MISSING_VISITOR` - Visitor information missing
- `MISSING_HOST_ID` - Host employee ID missing
- `MISSING_PURPOSE` - Purpose field missing
- `INVALID_AADHAAR_FORMAT` - Invalid Aadhaar number format

### Business Logic Errors  
- `UNAUTHORIZED_HOST` - Employee not authorized as HOST
- `INVALID_HOST_ID` - Host employee ID doesn't exist
- `VISIT_NOT_FOUND` - Visit ID not found for checkout

### System Errors
- `CHECKIN_FAILED` - Internal error during check-in
- `CHECKOUT_FAILED` - Internal error during checkout

## Migration Notes

### From Previous Services

If migrating from the separate reception and processing services:

1. **Database**: No schema changes required (outbox table can be dropped)
2. **Configuration**: Combine configs from both services
3. **Deployment**: Replace both services with this single service
4. **Monitoring**: Update monitoring to point to new service endpoints

### Kafka Consumer Updates

Downstream services consuming Kafka events should expect the same event structure, but with improved:
- More consistent event timestamps
- Enhanced correlation IDs
- Better error event handling

## Security Considerations

- All API endpoints require JWT authentication
- ID proof information is hashed using SHA-256
- Database connections use SSL/TLS in production
- Kafka connections support SASL/SSL authentication
- Input validation prevents injection attacks
- Request rate limiting recommended for production

## Performance

### Improvements over Previous Architecture
- Reduced network overhead (no inter-service calls)
- Lower latency for check-in/check-out operations
- Simplified error handling and recovery
- Better resource utilization

### Scaling Considerations
- Stateless service design enables horizontal scaling
- Database connection pooling for optimal performance
- Kafka producer configurations tuned for throughput
- Consider read replicas for read-heavy operations

## Support

For issues or questions:
1. Check the logs in `/logs/application.log`
2. Verify database and Kafka connectivity
3. Review configuration settings
4. Monitor Kafka consumer lag for downstream services