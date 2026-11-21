# ==============================================================================
# SupportHUB Backend — Production Multi-Stage Dockerfile
# Optimized for Render.com Free Web Service (Java 17 / Spring Boot 3.2.3)
# ==============================================================================

# Stage 1: Build JAR using Maven & OpenJDK 17
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /workspace

# 1. Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:resolve -B

# 2. Copy application source and build production executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal, secure production runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install curl for container health check probes
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Create least-privilege non-root user
RUN groupadd -g 1001 supporthub && useradd -u 1001 -g supporthub -m -d /app -s /bin/bash supporthub

# Ensure persistent local uploads directory exists with correct ownership
RUN mkdir -p /app/uploads && chown -R supporthub:supporthub /app

# Copy built Spring Boot artifact
COPY --from=builder --chown=supporthub:supporthub /workspace/target/customer-support-system-1.0.0.jar app.jar

# Render dynamically assigns PORT via environment variable (default: 8080)
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

# Switch to non-root user
USER supporthub

# Container healthcheck using public health endpoint
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:${PORT}/api/categories/active || exit 1

# Launch Spring Boot with dynamic port binding and production profile
ENTRYPOINT ["sh", "-c", "java -XX:TieredStopAtLevel=1 -Dserver.port=${PORT} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar app.jar"]
