# Complete Setup Guide - Visitor Management System

This guide walks you through **everything** you need to start before running the application.

---

## 📋 Prerequisites Checklist

Before starting, ensure you have:
- ✅ **Java 17 or 21** installed (NOT Java 25)
- ✅ **sbt** (Scala Build Tool) installed
- ✅ **Kafka** installed (includes ZooKeeper for older versions, or use KRaft mode for newer Kafka)
- ✅ **Postman** or similar API client (for testing)

---

## 🚀 Step-by-Step Startup Guide

### **STEP 1: Verify Java Version**

```bash
java -version
```

**Expected output:** Should show Java 17 or 21 (NOT 25)

**If you see Java 25:**
```bash
# Install Java 21 via Homebrew
brew install openjdk@21

# Set JAVA_HOME for this terminal session
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

# Verify again
java -version
```

---

### **STEP 2: Start Kafka (Required)**

Kafka needs to be running before starting the application. Choose **ONE** method:

#### **Option A: Using Homebrew (macOS)**

**For Kafka 2.x (with ZooKeeper):**
```bash
# Terminal 1: Start ZooKeeper
zookeeper-server-start /opt/homebrew/etc/kafka/zookeeper.properties

# Terminal 2: Start Kafka (in a NEW terminal)
kafka-server-start /opt/homebrew/etc/kafka/server.properties
```

**For Kafka 3.x+ (KRaft mode - no ZooKeeper needed):**
```bash
# Only one command needed
kafka-server-start /opt/homebrew/etc/kafka/server.properties
```

**If commands not found, find Kafka installation:**
```bash
# Find where Kafka is installed
brew info kafka

# Then use the full path, e.g.:
/opt/homebrew/opt/kafka/bin/kafka-server-start /opt/homebrew/etc/kafka/server.properties
```

#### **Option B: Using Docker (Easiest)**

If you have Docker installed:
```bash
# Start Kafka and ZooKeeper in one command
docker run -d \
  --name kafka \
  -p 9092:9092 \
  -e KAFKA_ZOOKEEPER_CONNECT=localhost:2181 \
  apache/kafka:latest
```

Or use Docker Compose (create `docker-compose.yml`):
```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

Then run:
```bash
docker-compose up -d
```

#### **Verify Kafka is Running:**

```bash
# Check if Kafka is listening on port 9092
lsof -i :9092

# Or test with Kafka console tools
kafka-topics --bootstrap-server localhost:9092 --list
```

**Expected:** Should show existing topics or no error.

---

### **STEP 3: Create Kafka Topics (Optional - Auto-created if enabled)**

Topics are usually auto-created, but you can create them manually:

```bash
# IT Support notifications
kafka-topics --bootstrap-server localhost:9092 --create --topic visitor-it-notifications --partitions 1 --replication-factor 1

# Security notifications
kafka-topics --bootstrap-server localhost:9092 --create --topic visitor-security-notifications --partitions 1 --replication-factor 1

# Check-out notifications
kafka-topics --bootstrap-server localhost:9092 --create --topic visitor-checkout-notifications --partitions 1 --replication-factor 1
```

---

### **STEP 4: Start the Play Framework Application**

**Terminal 1: Main Application**
```bash
cd /Users/racit/IdeaProjects/Scala_App

# Make sure Java 17/21 is active
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

# Start Play application
sbt run
```

**Wait for:**
```
INFO  p.c.s.AkkaHttpServer - Listening for HTTP on /[0:0:0:0:0:0:0:0]:9000
```

**✅ Application is ready when you see this!**

---

### **STEP 5: Start the Messaging Microservice (Optional but Recommended)**

**Terminal 2: Messaging Service**
```bash
cd /Users/racit/IdeaProjects/Scala_App/messaging-service

# Start messaging service
sbt run
```

**Wait for:**
```
Messaging service is running. Press ENTER to stop.
IT Notification Consumer started
Security Notification Consumer started
Check-out Notification Consumer started
```

**This service:**
- Consumes messages from Kafka topics
- Processes IT and Security notifications
- Logs all visitor activities

---

## 📊 Startup Order Summary

```
1. ✅ Java 17/21 active
2. ✅ Kafka running (port 9092)
3. ✅ Play app running (port 9000)  ← Main API
4. ✅ Messaging service running     ← Optional but recommended
```

---

## 🧪 Testing the Setup

### **Test 1: Check if Play API is running**
```bash
curl http://localhost:9000/api/visitors/active
```

**Expected:** `[]` (empty array) or JSON response

### **Test 2: Check if Kafka is accessible**
```bash
kafka-topics --bootstrap-server localhost:9092 --list
```

**Expected:** List of topics or no error

---

## 🛑 Stopping Everything

**Stop in reverse order:**

1. **Stop Play App:** Press `Ctrl+C` in Terminal 1
2. **Stop Messaging Service:** Press `ENTER` in Terminal 2 (or `Ctrl+C`)
3. **Stop Kafka:**
   ```bash
   # If using Homebrew
   kafka-server-stop
   zookeeper-server-stop
   
   # If using Docker
   docker stop kafka
   docker stop zookeeper
   # OR
   docker-compose down
   ```

---

## ⚠️ Troubleshooting

### **Error: "Kafka connection refused"**
- **Solution:** Make sure Kafka is running on port 9092
- **Check:** `lsof -i :9092`

### **Error: "Java version is 25"**
- **Solution:** Switch to Java 17/21 using the commands in STEP 1

### **Error: "Port 9000 already in use"**
- **Solution:** 
  ```bash
  lsof -ti:9000 | xargs kill -9
  ```

### **Error: "Topics not found"**
- **Solution:** Topics are auto-created when first message is sent, or create them manually (STEP 4)

---

## 📝 Environment Variables (Optional for email)

You can optionally set these to override email configuration (instead of hard-coding in `conf/application.conf`):

```bash
export EMAIL_USER="your-gmail-account@gmail.com"        # must match email.from
export EMAIL_PASSWORD="your-gmail-app-password"         # Gmail App Password only
export APPLICATION_SECRET="your-secret-key"
```

- `EMAIL_USER` MUST be the same Gmail account as `email.from` in `conf/application.conf`.
- `EMAIL_PASSWORD` MUST be a **Gmail App Password** — normal Gmail password will **NOT** work.

---

## ✅ Quick Start Script

See `start-all.sh` for an automated startup script (if created).

---

## 📚 Next Steps

Once everything is running:
1. Open Postman
2. Test the endpoints (see Postman guide)
3. Check logs in both terminals for activity

**Happy Testing! 🎉**

