# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Use Maven + JDK 17 to compile and package the app
FROM maven:3.9.5-eclipse-temurin-17-focal AS builder

WORKDIR /app

# Copy pom.xml first — Docker caches this layer separately.
# If only source code changes (not dependencies), Maven doesn't re-download jars.
# This makes rebuilds much faster.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Now copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Use a slim JRE (not full JDK) — smaller image, smaller attack surface
FROM eclipse-temurin:17-jre-focal

WORKDIR /app

# Copy only the built jar from the builder stage
# This is the multi-stage build pattern — the final image has no Maven, no source
COPY --from=builder /app/target/*.jar app.jar

# Non-root user for security
RUN groupadd -r smartqueue && useradd -r -g smartqueue smartqueue
USER smartqueue

# Document which port the app listens on (doesn't actually expose it)
EXPOSE 8080

# Health check — Docker uses this to know when the app is ready
# Waits 30s before first check (app startup time), then checks every 10s
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]