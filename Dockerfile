# ================================================================
# BUILD STAGE
# ================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Dependency cache üçün əvvəlcə yalnız pom.xml.
COPY pom.xml .

RUN mvn \
    --batch-mode \
    dependency:go-offline

# Sonra application source.
COPY src ./src

# Full regression ayrıca "mvn clean verify" mərhələsində edilir.
# Image build zamanı testləri yenidən işə salmırıq,
# amma project compile/package olunur.
RUN mvn \
    --batch-mode \
    -DskipTests \
    clean package


# ================================================================
# RUNTIME STAGE
# ================================================================
FROM eclipse-temurin:21-jre-jammy

# ------------------------------------------------
# Minimal runtime dependency:
# curl yalnız container healthcheck üçündür.
# ------------------------------------------------
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# ------------------------------------------------
# Dedicated non-root operating-system user
# ------------------------------------------------
RUN groupadd \
        --system \
        cvscanner \
    && useradd \
        --system \
        --gid cvscanner \
        --home-dir /app \
        --create-home \
        --shell /usr/sbin/nologin \
        cvscanner

WORKDIR /app

# ------------------------------------------------
# Writable application storage
# ------------------------------------------------
RUN mkdir -p /app/storage/uploads \
    && chown -R cvscanner:cvscanner /app

# ------------------------------------------------
# Spring Boot executable JAR
# ------------------------------------------------
COPY \
    --from=build \
    --chown=cvscanner:cvscanner \
    /workspace/target/cvscanner-0.0.1-SNAPSHOT.jar \
    /app/cvscanner.jar

# ------------------------------------------------
# Production defaults
# ------------------------------------------------
ENV SPRING_PROFILES_ACTIVE=prod

# JVM reads JAVA_TOOL_OPTIONS automatically.
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# ------------------------------------------------
# Docker-level healthcheck
#
# /readyz checks:
# - Spring readiness state
# - PostgreSQL
# - Redis when fail-closed rate limiting is enabled
# ------------------------------------------------
HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=30s \
    --retries=3 \
    CMD curl \
        --fail \
        --silent \
        --show-error \
        http://127.0.0.1:8080/readyz \
        > /dev/null \
        || exit 1

# ------------------------------------------------
# Never run the application as root
# ------------------------------------------------
USER cvscanner

ENTRYPOINT ["java", "-jar", "/app/cvscanner.jar"]