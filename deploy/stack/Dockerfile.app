# syntax=docker/dockerfile:1
# Production image for show-standard (EC2 + RDS).
# Build context: repository root.
# Multi-stage: C-end frontend + SaaS frontend → Maven fat jar → JRE runtime.
# Spring profile via SPRING_PROFILES_ACTIVE (default ec2). Secrets come from env/app.env, never baked in.

# ---------- C-end (buyer) SPA → resources/static ----------
FROM node:20-alpine AS node-cend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# vite.config outDir: ../src/main/resources/static → /build/src/main/resources/static
RUN npm run build

# ---------- SaaS admin SPA → resources/static-saas (base /saas/) ----------
FROM node:20-alpine AS node-saas
WORKDIR /build/frontend-saas
COPY frontend-saas/package.json frontend-saas/package-lock.json ./
RUN npm ci
COPY frontend-saas/ ./
# vite.config: base /saas/, outDir ../src/main/resources/static-saas
RUN npm run build

# ---------- Maven package (embeds both static trees) ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
COPY --from=node-cend /build/src/main/resources/static ./src/main/resources/static
COPY --from=node-saas /build/src/main/resources/static-saas ./src/main/resources/static-saas
RUN mvn -B -DskipTests package \
    && cp target/ddmo-1.0.0.jar /src/app.jar

# ---------- Runtime ----------
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /root/.show/backups

WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar

# Profile via env (do not hardcode cloud). Secrets/JWT/AES from compose --env-file / app.env.
ENV SPRING_PROFILES_ACTIVE=ec2 \
    JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

# 2xx/3xx/4xx all mean the process is up (401/403/404 without auth is fine)
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
  CMD curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/ | grep -qE '^[234]' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
