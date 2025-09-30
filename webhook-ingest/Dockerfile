# Multi-stage Dockerfile for webhook-ingest service
# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /app

# Copy parent POM first (for dependency resolution)
COPY pom.xml /app/pom.xml
COPY modules/webhook-ingest/pom.xml /app/modules/webhook-ingest/pom.xml
COPY modules/reviewer-service/pom.xml /app/modules/reviewer-service/pom.xml

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -f /app/pom.xml -pl modules/webhook-ingest

# Copy source code
COPY modules/webhook-ingest/src /app/modules/webhook-ingest/src

# Build the application
RUN mvn clean package -f /app/pom.xml -pl modules/webhook-ingest -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Create non-root user for security
RUN addgroup -g 1001 appuser && adduser -D -u 1001 -G appuser appuser

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/modules/webhook-ingest/target/webhook-ingest-*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app
USER appuser

# Expose port
EXPOSE 9091

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:9091/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
