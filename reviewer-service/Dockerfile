# Multi-stage Dockerfile for reviewer-service
# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /app

# Copy parent POM and all service POMs
COPY pom.xml /app/pom.xml
COPY modules/webhook-ingest/pom.xml /app/webhook-ingest/pom.xml
COPY modules/reviewer-service/pom.xml /app/reviewer-service/pom.xml

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -f /app/pom.xml -pl reviewer-service

# Copy source code
COPY modules/reviewer-service/src /app/reviewer-service/src

# Build the application
RUN mvn clean package -f /app/pom.xml -pl reviewer-service -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 appuser && adduser -D -u 1001 -G appuser appuser

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/reviewer-service/target/reviewer-service-*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app
USER appuser

# Expose port
EXPOSE 9092

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:9092/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
