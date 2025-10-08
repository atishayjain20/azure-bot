# Azure PR Reviewer

Automatically review Azure DevOps pull requests using AI. This service receives webhook events, analyzes code changes, and posts intelligent review comments.

## Quick Start with Docker

### 1. Setup Environment
```bash
# Copy the example file
cp .env.example .env

# Edit .env with your credentials
# - Azure DevOps PAT
# - Azure OpenAI API key
# - Langfuse credentials
```

### 2. Run with Docker Compose
```bash
# Start all services (app + Kafka + UI)
docker-compose up -d --build

# View logs
docker-compose logs -f azure-pr-reviewer

# Stop services
docker-compose down
```

### 3. Expose to Azure DevOps
```bash
# Install ngrok: https://ngrok.com/
ngrok http 9091

# Copy the HTTPS URL (e.g., https://abc123.ngrok-free.app)
```

### 4. Configure Azure DevOps Service Hook
1. Go to Project Settings → Service Hooks
2. Create subscription:
   - **Service**: Web Hooks
   - **Event**: Pull request created
   - **URL**: `https://your-ngrok-url.ngrok-free.app/webhooks/azure/pr`

## Services Included

- **Azure PR Reviewer**: Main application (port 9091)
- **Kafka**: Message broker (port 29092)
- **Kafka UI**: Web interface (port 8085)

## Configuration

Edit `.env` file with your settings:

```bash
# Azure DevOps
ADO_BASEURL=https://dev.azure.com/your-org
ADO_PROJECTID=your-project-id
ADO_PAT=your-personal-access-token

# Azure OpenAI
SPRING_AI_AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
SPRING_AI_AZURE_OPENAI_API_KEY=your-api-key

# Langfuse (optional)
OTEL_EXPORTER_OTLP_ENDPOINT=https://cloud.langfuse.com/api/public/otel/v1/traces
OTEL_EXPORTER_OTLP_HEADERS=Authorization=Basic your-base64-auth
```

## How It Works

1. Azure DevOps sends PR webhook → `/webhooks/azure/pr`
2. Service fetches PR changes and file diffs
3. AI analyzes code changes and generates review
4. Comments are posted back to the PR
5. All data is logged and traced

## Monitoring

- **Application**: http://localhost:9091/actuator/health
- **Kafka UI**: http://localhost:8085
- **Logs**: `docker-compose logs -f azure-pr-reviewer`
