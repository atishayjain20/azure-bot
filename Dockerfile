# Multi-stage build for Azure PR Reviewer Webhook

FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app

# Cache deps
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline

# Build
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# Create writable dirs for logs and dumps
RUN mkdir -p /app/logs /app/prompt-logs /app/response-logs /app/webhook-dumps /app/ado-pr-changes /app/ado-pr-files

# Copy built jar
COPY --from=build /app/target/azure-pr-reviewer-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 9091

# Healthcheck (optional)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s CMD curl -fsS http://localhost:9091/actuator/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]


