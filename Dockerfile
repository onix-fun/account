# Stage 1: Frontend (Build or Use existing)
FROM node:22-alpine AS frontend-build
WORKDIR /app
COPY frontend/package*.json ./
COPY frontend/ ./
# If dist already exists (provided from outside), we skip npm build
RUN if [ ! -d "dist" ]; then npm ci && npm run build; fi

# Stage 2: Backend (Build or Use existing)
FROM maven:3.9.9-eclipse-temurin-23 AS backend-build
WORKDIR /src
COPY backend/pom.xml ./
# Only download dependencies if we actually need to build
COPY backend/src ./src
COPY backend/target ./target
# If the shaded jar already exists, skip maven build
RUN if [ ! -f "target/"*"-with-dependencies.jar" ]; then \
    mvn --batch-mode dependency:go-offline && \
    mvn --batch-mode -DskipTests package; \
    fi

# Stage 3: Runtime
FROM eclipse-temurin:23-jre
WORKDIR /app

# s6-overlay version
ARG S6_OVERLAY_VERSION=3.2.0.2

# Install dependencies and s6-overlay
RUN apt-get update && apt-get install -y nginx gettext bash curl xz-utils && \
    rm -rf /var/lib/apt/lists/* && \
    if [ "$(uname -m)" = "x86_64" ]; then ARCH="x86_64"; \
    elif [ "$(uname -m)" = "aarch64" ]; then ARCH="aarch64"; \
    else echo "Unsupported architecture: $(uname -m)"; exit 1; fi && \
    curl -L -s https://github.com/just-containers/s6-overlay/releases/download/v${S6_OVERLAY_VERSION}/s6-overlay-noarch.tar.xz | tar -Jxpf - -C / && \
    curl -L -s https://github.com/just-containers/s6-overlay/releases/download/v${S6_OVERLAY_VERSION}/s6-overlay-${ARCH}.tar.xz | tar -Jxpf - -C /

# Setup appuser
RUN useradd -M -u 1001 appuser

# Copy Frontend artifacts
COPY --from=frontend-build /app/dist /usr/share/nginx/html

# Copy Backend artifacts
COPY --from=backend-build /src/target/*-with-dependencies.jar /app/app.jar

# Copy OTel agent
ARG OTEL_JAVA_AGENT_VERSION=2.27.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

# Copy s6-overlay rootfs
COPY docker/rootfs/ /

# Fix permissions
RUN mkdir -p /var/lib/nginx/tmp /var/log/nginx /run/nginx /etc/nginx/conf.d && \
    chown -R appuser:appuser /var/lib/nginx /var/log/nginx /run/nginx /etc/nginx/conf.d /usr/share/nginx/html /app /otel /etc/account && \
    chmod +x /etc/s6-overlay/s6-rc.d/*/run /etc/s6-overlay/s6-rc.d/init-config/up /etc/s6-overlay/s6-rc.d/*/finish /usr/local/bin/migrate 2>/dev/null || true

# s6-overlay entrypoint
ENTRYPOINT ["/init"]
