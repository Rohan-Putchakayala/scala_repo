# 🚀 Quick Start Guide

## **TL;DR - Fastest Way to Start Everything**

### **Method 1: Using Scripts (Easiest)**

```bash
# Terminal 1: Start Kafka
./start-kafka.sh

# Terminal 2: Start Play App
./start-all.sh

# Terminal 3 (Optional): Start Messaging Service
cd messaging-service && sbt run
```

---

### **Method 2: Using Docker (Recommended)**

```bash
# Terminal 1: Start Kafka with Docker
docker-compose up -d

# Terminal 2: Start Play App
cd /Users/racit/IdeaProjects/Scala_App
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
sbt run

# Terminal 3 (Optional): Start Messaging Service
cd messaging-service && sbt run
```

---

### **Method 3: Manual (Step-by-Step)**

```bash
# 1. Set Java 17/21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

# 2. Start Kafka (choose one):
#    Option A: Homebrew
kafka-server-start /opt/homebrew/etc/kafka/server.properties

#    Option B: Docker
docker-compose up -d

# 3. Start Play App
sbt run

# 4. (Optional) Start Messaging Service
cd messaging-service && sbt run
```

---

## ✅ Verification Checklist

- [ ] Java 17 or 21 active (`java -version`)
- [ ] Kafka running on port 9092 (`lsof -i :9092`)
- [ ] Play app running on port 9000 (`curl http://localhost:9000/api/visitors/active`)

---

## 🛑 Stop Everything

```bash
# Stop Play App: Ctrl+C in Terminal 2
# Stop Messaging Service: Ctrl+C or ENTER in Terminal 3
# Stop Kafka:
docker-compose down
# OR
kafka-server-stop
zookeeper-server-stop
```

---

## 📝 Postman Endpoints

**Base URL:** `http://localhost:9000`

1. **Check-in:** `POST /api/visitors/check-in` (multipart/form-data)
2. **Check-out:** `PUT /api/visitors/check-out/:id`
3. **Active Visitors:** `GET /api/visitors/active`
4. **Visitor History:** `GET /api/visitors/history/:email`
5. **Get Visitor:** `GET /api/visitors/:id`

---

## ⚠️ Common Issues

| Issue | Solution |
|-------|----------|
| Java 25 error | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| Kafka not found | Install: `brew install kafka` or use Docker |
| Port 9000 in use | `lsof -ti:9000 \| xargs kill -9` |
| Kafka connection refused | Start Kafka first: `./start-kafka.sh` |

---

**For detailed setup, see `SETUP.md`**

