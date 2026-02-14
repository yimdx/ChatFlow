# ChatFlow - WebSocket Chat Server & Client

A scalable WebSocket-based chat system with performance analysis capabilities, built for CS6650 Assignment 1.

## Architecture

ChatFlow uses a dual-port architecture:
- **Port 8080**: HTTP health check endpoint (`/health`)
- **Port 8081**: WebSocket chat endpoint (`/chat/{roomId}`)

The system supports 20 chat rooms (rooms 1-20) with concurrent messaging capabilities.

## Project Structure

```
ChatFlow/
├── server/              # WebSocket server
├── client-part1/        # Basic client (500K messages)
├── client-part2/        # Performance analysis client
├── DESIGN.md           # Architecture & design document
├── VISUALIZATION_README.md  # Throughput visualization guide
└── visualize_throughput.py  # Python visualization script
```

## Prerequisites

- **Java**: 17 or higher
- **Maven**: 3.6 or higher
- **Git**: For cloning the repository
- **Python 3**: (Optional) For throughput visualization
- **matplotlib**: (Optional) `pip install matplotlib`

## 1. Server Deployment

### EC2 Deployment (Recommended)

1. **Launch EC2 Instance**
   - Instance type: t2.micro or larger
   - OS: Amazon Linux 2023 or Ubuntu
   - Security group: Open ports 22 (SSH), 8080 (HTTP), 8081 (WebSocket)

2. **Install Dependencies**
   ```bash
   # Amazon Linux
   sudo yum update -y
   sudo yum install -y git java-17-amazon-corretto maven screen
   
   # Ubuntu
   sudo apt update
   sudo apt install -y git openjdk-17-jdk maven screen
   ```

3. **Clone and Build**
   ```bash
   git clone https://github.com/yimdx/ChatFlow.git
   cd ChatFlow/server
   mvn clean package
   ```

4. **Run Server (Persistent with screen)**
   ```bash
   screen -dmS chatflow bash -c 'java -jar target/WebSocketServer-1.0-SNAPSHOT.jar 2>&1 | tee chatflow.log'
   ```

5. **Verify Server**
   ```bash
   # Check health endpoint
   curl http://localhost:8080/health
   # Expected: {"status":"healthy"}
   
   # View logs
   screen -r chatflow
   # Detach: Ctrl+A then D
   
   # Monitor logs
   tail -f chatflow.log
   ```

### Local Deployment

```bash
cd server
mvn clean compile exec:java -Dexec.mainClass="cs6650.assignment1.Main"
```

## 2. Client Usage

### Update Server URL

Before running clients, update the `SERVER_URL` in:
- `client-part1/src/main/java/cs6650/assignment1/Main.java`
- `client-part2/src/main/java/cs6650/assignment1/Main.java`

```java
// Replace with your EC2 public IP or localhost
private static final String SERVER_URL = "ws://YOUR_SERVER_IP:8081";

// Example:
// EC2: private static final String SERVER_URL = "ws://34.222.36.162:8081";
// Local: private static final String SERVER_URL = "ws://localhost:8081";
```

### Client Part 1 - Basic Load Testing

Sends 500,000 messages using 32 threads with warmup phase.

```bash
cd client-part1
mvn clean package -DskipTests
java -jar target/client-part1-1.0-SNAPSHOT.jar
```

**Output:**
- Total messages sent
- Success/failure counts
- Connection statistics
- Total runtime
- Throughput (messages/second)

### Client Part 2 - Performance Analysis

Sends 500,000 messages and generates detailed performance metrics.

```bash
cd client-part2
mvn clean package -DskipTests
java -jar target/client-part2-1.0-SNAPSHOT.jar
```

**Output Files** (in `client-part2/results/`):
- `metrics_YYYYMMDD_HHMMSS.csv`: Detailed per-message metrics
- `throughput_YYYYMMDD_HHMMSS.txt`: Throughput over time data

**Console Statistics:**
- Mean/median response time
- 95th/99th percentile latency
- Message type distribution
- Message count per room
- **Throughput per room** (messages/second)
ng

## Configuration

### Thread Configuration (client-part1 & client-part2)
```java
private static final int WARMUP_THREADS = 32;
private static final int WARMUP_MESSAGES_PER_THREAD = 1000;
private static final int TOTAL_MESSAGES = 500_000;
```

### Room Configuration
- Valid room IDs: 1-20
- Invalid rooms return error response

### Server won't start
```bash
# Check if ports are in use
sudo netstat -tulpn | grep 8080
sudo netstat -tulpn | grep 8081

# Kill existing processes
sudo kill -9 <PID>
```