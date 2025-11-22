# Quick Start Guide

Get the Visitor Management System up and running in 5 minutes!

## Prerequisites Check

Verify you have the required tools:

```bash
# Check Java version (need 11+)
java -version

# Check sbt
sbt --version

# Check if Kafka is installed
kafka-topics --version
# or
brew list | grep kafka
```

## Step 1: Install and Start Kafka

### On macOS:
```bash
# Install Kafka
brew install kafka

# Start Zookeeper
brew services start zookeeper

# Start Kafka
brew services start kafka

# Verify Kafka is running
brew services list | grep kafka
```

### On Linux:
```bash
# Download Kafka (if not already installed)
wget https://downloads.apache.org/kafka/3.6.1/kafka_2.13-3.6.1.tgz
tar -xzf kafka_2.13-3.6.1.tgz
cd kafka_2.13-3.6.1

# Start Zookeeper (in terminal 1)
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka (in terminal 2)
bin/kafka-server-start.sh config/server.properties
```

## Step 2: Create Kafka Topics

```bash
# For macOS (Homebrew installation)
kafka-topics --create --topic visitor-it-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic visitor-security-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic visitor-checkout-notifications --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# Verify topics were created
kafka-topics --list --bootstrap-server localhost:9092
```

## Step 3: Start the Main Application

Open a new terminal in the project directory:

```bash
cd /Users/racit/IdeaProjects/Scala_App

# Compile and run the Play application
sbt run
```

Wait for the message: `(Server started, use Enter to stop and go back to the console...)`

The API will be available at: **http://localhost:9000**

## Step 4: Start the Messaging Microservice

Open another terminal in the project directory:

```bash
cd /Users/racit/IdeaProjects/Scala_App

# Run the messaging service
sbt "messaging-service/run"
```

You should see:
```
Messaging Service is running and listening for messages...
Topics subscribed:
  - visitor-it-notifications
  - visitor-security-notifications
  - visitor-checkout-notifications
```

## Step 5: Test the System

### Option 1: Use the test script

```bash
./test-api.sh
```

### Option 2: Manual test with curl

Check in a visitor:
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

You should see a JSON response with `checkInId` and `visitorId`.

Check active visitors:
```bash
curl http://localhost:9000/api/visitors/active
```

Check out a visitor (replace `1` with the checkInId from check-in):
```bash
curl -X PUT http://localhost:9000/api/visitors/check-out/1
```

## What Happens Behind the Scenes?

When you check in a visitor:

1. ✅ **Main Application** saves visitor data and ID reference number
2. 📧 **Email Service** sends notification to host employee
3. 📧 **Email Service** sends WiFi credentials to visitor
4. 📨 **Kafka** publishes messages to IT and Security topics
5. 🎯 **Messaging Service** IT Support Actor processes the message
6. 🎯 **Messaging Service** Security Actor processes the message

Check the terminal running the messaging service to see the actors processing notifications!

## Monitor Kafka Messages (Optional)

To see messages being published to Kafka topics:

```bash
# Watch IT notifications
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic visitor-it-notifications --from-beginning

# Watch Security notifications (in another terminal)
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic visitor-security-notifications --from-beginning
```

## Configure Email (Required for Gmail SMTP)

To enable actual email sending via Gmail:

1. Edit `conf/application.conf` and set:
   - `email.smtp.user` = your Gmail address (e.g. `my.company.app@gmail.com`)
   - `email.from` = the **same Gmail address** as `email.smtp.user`
   - `email.smtp.password` = the **Gmail App Password** for that account (not your normal password)

2. (Optional) Instead of hard-coding, you can set environment variables:

```bash
export EMAIL_USER="your-gmail-account@gmail.com"        # must match email.from
export EMAIL_PASSWORD="your-gmail-app-password"         # Gmail App Password only

# Restart the application
sbt run
```

**Note for Gmail (mandatory):**
1. Enable 2-Factor Authentication on your Google account
2. Generate an App Password at: https://myaccount.google.com/apppasswords
3. Use the App Password (not your regular Gmail password) as `email.smtp.password` / `EMAIL_PASSWORD`
4. Make sure the From address and SMTP user are the **same Gmail account**

## Stopping the System

1. **Stop Messaging Service**: Press ENTER in its terminal
2. **Stop Main Application**: Press ENTER in its terminal
3. **Stop Kafka** (macOS): `brew services stop kafka`
4. **Stop Zookeeper** (macOS): `brew services stop zookeeper`

## Troubleshooting

### Port Already in Use
If port 9000 is in use, you can change it:
```bash
sbt "run -Dhttp.port=8080"
```

### Kafka Connection Error
Ensure Kafka and Zookeeper are running:
```bash
# macOS
brew services list

# Check if processes are running
ps aux | grep kafka
ps aux | grep zookeeper
```

### Database Errors
The application uses an in-memory H2 database by default. Data is lost on restart. This is normal for development.

### Can't Find sbt
Install sbt:
```bash
# macOS
brew install sbt

# Linux
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
sudo apt-get update
sudo apt-get install sbt
```

## Next Steps

- Read the full [README.md](README.md) for detailed documentation
- Explore the API endpoints in [conf/routes](conf/routes)
- Check the database schema in [conf/evolutions/default/1.sql](conf/evolutions/default/1.sql)
- Review the notification flow in the actors

## Project Structure

```
Scala_App/
├── app/                          # Main Play application
│   ├── actors/                   # Akka actors
│   ├── controllers/              # REST API controllers
│   ├── models/                   # Domain models
│   ├── repositories/             # Database repositories
│   └── services/                 # Business logic services
├── conf/                         # Configuration files
│   ├── application.conf          # Main config
│   ├── routes                    # API routes
│   └── evolutions/               # Database migrations
├── messaging-service/            # Separate microservice
│   └── src/main/scala/
│       ├── actors/               # IT Support & Security actors
│       ├── models/               # Message models
│       └── services/             # Kafka consumer
└── README.md                     # Full documentation
```

Happy coding! 🚀
