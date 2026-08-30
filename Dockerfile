FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .

RUN mvn \
    --batch-mode \
    dependency:go-offline

COPY src ./src

RUN mvn \
    --batch-mode \
    -DskipTests \
    clean package


FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

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

RUN mkdir -p /app/storage/uploads \
    && chown -R cvscanner:cvscanner /app

COPY \
    --from=build \
    --chown=cvscanner:cvscanner \
    /workspace/target/cvscanner.jar \
    /app/cvscanner.jar

ENV SPRING_PROFILES_ACTIVE=prod

ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

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

USER cvscanner

ENTRYPOINT ["java", "-jar", "/app/cvscanner.jar"]