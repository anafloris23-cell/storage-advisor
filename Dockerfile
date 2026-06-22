# ── Stage 1: build the React frontend ──
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ── Stage 2: build the Spring Boot backend (with the frontend bundled in) ──
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY pom.xml ./
COPY src ./src
# Serve the built frontend from Spring Boot (static resources).
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn -q -DskipTests package

# ── Stage 3: runtime image (Java + solc) ──
FROM eclipse-temurin:17-jre
# Install the solc binary (Solidity compiler), invoked as an external process.
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && \
    curl -L -o /usr/local/bin/solc \
      https://github.com/ethereum/solidity/releases/download/v0.8.34/solc-static-linux && \
    chmod +x /usr/local/bin/solc && \
    apt-get purge -y curl && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=backend /app/target/storageadvisor-0.1.0-SNAPSHOT.jar app.jar
# Data needed at runtime: synthetic examples + the frozen bulk report.
COPY dataset ./dataset
COPY reports ./reports

# Keep the JVM heap modest for small free-tier instances.
ENV JAVA_TOOL_OPTIONS="-Xmx320m"
CMD ["java", "-jar", "app.jar"]
