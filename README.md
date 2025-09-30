# Azure DevOps PR Reviewer

AI-powered pull request reviewer that automatically analyzes code changes and provides intelligent feedback.

## 🚀 Quick Start

### Prerequisites
- Docker Desktop
- Git

### 1. Setup Environment
```bash
git clone <repository-url>
cd ADO-Microservices/modules
```

### 2. Configure Services
Create `.env` files with your credentials:

**webhook-ingest/.env:**
```env
ADO_BASE_URL=https://dev.azure.com/your-org
ADO_PROJECT_ID=your-project-id
ADO_PAT=your-personal-access-token
KAFKA_BOOTSTRAPSERVERS=localhost:9092
KAFKA_SECURITY_PROTOCOL=PLAINTEXT
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="admin-secret";
KAFKA_TOPIC_AZURE_PR_EVENTS=azure-pr-events
SERVER_PORT=9091
```

**reviewer-service/.env:**
```env
ADO_BASE_URL=https://dev.azure.com/your-org
ADO_PROJECT_ID=your-project-id
ADO_PAT=your-personal-access-token
AZURE_OPENAI_ENDPOINT=https://your-openai-resource.openai.azure.com/
AZURE_OPENAI_API_KEY=your-openai-api-key
AZURE_OPENAI_MODEL=gpt-4
OTLP_ENDPOINT=https://your-otlp-endpoint
OTLP_AUTH_HEADER=Bearer your-token
KAFKA_BOOTSTRAPSERVERS=localhost:9092
KAFKA_SECURITY_PROTOCOL=PLAINTEXT
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="admin-secret";
KAFKA_TOPIC_AZURE_PR_EVENTS=azure-pr-events
SERVER_PORT=9092
```

### 3. Run Application
```bash
docker-compose up --build -d
```

### 4. Verify Services
```bash
docker-compose ps
```

## 🏗️ Architecture

- **webhook-ingest** (Port 9091): Receives Azure DevOps webhooks → Kafka
- **reviewer-service** (Port 9092): Consumes Kafka events → AI review → Azure DevOps
- **Kafka**: Message broker between services
- **Kafka UI** (Port 8080): Monitor messages and topics

## 🛠️ Management

### Start/Stop
```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# Rebuild and start
docker-compose up --build -d
```

### Monitor
```bash
# View logs
docker-compose logs -f

# Check health
curl http://localhost:9091/actuator/health  # webhook-ingest
curl http://localhost:9092/actuator/health  # reviewer-service

# Kafka UI
open http://localhost:8080
```

### Troubleshooting
```bash
# Check service status
docker-compose ps

# View specific logs
docker-compose logs webhook-ingest
docker-compose logs reviewer-service

# Clean restart
docker-compose down -v && docker-compose up --build -d
```