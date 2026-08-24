# CVScanner

Production-oriented CV ingestion, processing, search, export and operational management backend built with **Java 21** and **Spring Boot 4**.

CVScanner accepts CV archives, processes candidate documents asynchronously, persists structured candidate information in PostgreSQL, exposes searchable candidate APIs, supports CSV/XLSX exports, provides distributed Redis-backed rate limiting, and includes production-oriented health checks, cleanup jobs, security controls and release verification tooling.

---

## Table of Contents

- [Overview](#overview)
- [Core Features](#core-features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Application Flow](#application-flow)
- [Security](#security)
- [Rate Limiting](#rate-limiting)
- [Batch Processing](#batch-processing)
- [Upload Storage Cleanup](#upload-storage-cleanup)
- [Candidate Search and Export](#candidate-search-and-export)
- [Health and Observability](#health-and-observability)
- [Configuration Profiles](#configuration-profiles)
- [Local Development](#local-development)
- [Docker](#docker)
- [Production Deployment](#production-deployment)
- [Environment Variables](#environment-variables)
- [Testing](#testing)
- [Release Gate](#release-gate)
- [Database Migrations](#database-migrations)
- [Persistent Data](#persistent-data)
- [Rollback](#rollback)
- [Operational Notes](#operational-notes)
- [Known Architectural Limitations](#known-architectural-limitations)

---

# Overview

CVScanner is a backend service designed for controlled ingestion and processing of CV files.

The system focuses on the following workflow:

```text
CV Archive Upload
        |
        v
Upload Validation
        |
        v
Safe Extraction
        |
        v
Spring Batch Processing
        |
        v
Document Parsing
        |
        v
Candidate Persistence
        |
        +--------------------+
        |                    |
        v                    v
Candidate Search        Processing Failures
        |
        +--------------------+
        |                    |
        v                    v
    CSV Export           XLSX Export
```

The application is designed with production concerns in mind:

```text
Authentication / Authorization
            |
            v
       Spring Security
            |
            v
       Rate Limiting
            |
            v
       Business APIs
            |
      +-----+------+
      |            |
      v            v
 PostgreSQL      Redis
      |
      v
Persistent business data
```

---

# Core Features

CVScanner currently provides:

- CV archive upload
- ZIP extraction with safety controls
- Asynchronous CV processing
- Spring Batch based processing
- Candidate persistence
- Candidate filtering and pagination
- Candidate CSV export
- Candidate XLSX export
- Processing failure tracking
- Upload progress/status tracking
- Batch retry and restart support
- Persistent upload storage
- Retention-based upload cleanup
- Distributed cleanup locking
- JWT authentication
- Role-based authorization
- Explicit endpoint allowlisting
- Distributed Redis-backed rate limiting
- Liveness and readiness probes
- Actuator metrics
- Correlation IDs
- HTTP access logging
- Flyway database migrations
- Docker development environment
- Production Docker image
- Hardened production Docker Compose configuration
- Automated production smoke verification
- Automated final release gate

---

# Architecture

High-level runtime architecture:

```text
                       +----------------------+
                       |      Client/API      |
                       +----------+-----------+
                                  |
                                  | Bearer JWT
                                  v
                       +----------------------+
                       |   Spring Security    |
                       | JWT + RBAC + DenyAll |
                       +----------+-----------+
                                  |
                                  v
                       +----------------------+
                       |    Rate Limiting     |
                       |      Bucket4j        |
                       +----------+-----------+
                                  |
                                  v
              +------------------------------------------+
              |                 CVScanner                 |
              |                                          |
              |  Upload API                              |
              |  Candidate API                           |
              |  Export API                              |
              |  Failure API                             |
              |  Cleanup                                 |
              |  Spring Batch                            |
              +------------+-----------------------------+
                           |
             +-------------+-------------+
             |                           |
             v                           v
    +------------------+        +------------------+
    |    PostgreSQL    |        |      Redis       |
    |                  |        |                  |
    | Candidates       |        | Rate-limit state |
    | Uploads          |        +------------------+
    | Failures         |
    | Batch metadata   |
    | Cleanup metadata |
    +------------------+

             |
             v
    +-----------------------+
    | Persistent CV Storage |
    +-----------------------+
```

---

# Technology Stack

## Backend

```text
Java 21
Spring Boot 4.1
Spring Web MVC
Spring Data JPA
Spring Security
Spring OAuth2 Resource Server
Spring Batch
Spring Boot Actuator
```

## Persistence

```text
PostgreSQL 16
Hibernate ORM
Flyway
HikariCP
```

## Distributed Infrastructure

```text
Redis 7
Lettuce
Bucket4j
```

## Document Processing

```text
Apache Tika
Apache POI
```

## Testing

```text
JUnit 5
Spring Boot Test
MockMvc
Mockito
Testcontainers
Maven Surefire
Maven Failsafe
```

## Runtime / Deployment

```text
Docker
Docker Compose
Eclipse Temurin JRE 21
Maven 3.9
PowerShell release tooling
```

---

# Project Structure

Representative structure:

```text
CVScanner/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/adil/cvscanner/
│   │   │       ├── candidate/
│   │   │       ├── common/
│   │   │       ├── config/
│   │   │       ├── processing/
│   │   │       ├── ratelimit/
│   │   │       ├── security/
│   │   │       ├── upload/
│   │   │       └── CvScannerApplication.java
│   │   │
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-dev.yml
│   │       ├── application-staging.yml
│   │       ├── application-prod.yml
│   │       └── db/
│   │           └── migration/
│   │
│   └── test/
│       ├── java/
│       └── resources/
│
├── scripts/
│   ├── production-smoke.ps1
│   └── release-gate.ps1
│
├── Dockerfile
├── docker-compose.yml
├── docker-compose.prod.yml
├── .dockerignore
├── .env.prod.example
├── pom.xml
└── README.md
```

---

# Application Flow

## 1. Upload

A recruiter or administrator sends a CV upload request.

```text
POST /api/v1/uploads
```

The application validates the request before starting asynchronous processing.

The upload layer protects the application against unsafe or excessively large archive contents.

Important upload configuration includes:

```yaml
app:
  upload:
    storage-root: ./storage/uploads
    max-entries: 5000
    max-extracted-size: 1GB
    max-single-file-size: 25MB
```

---

## 2. Safe Extraction

Uploaded archives are extracted into controlled application storage.

The extraction layer is designed to enforce configured limits such as:

```text
Maximum ZIP entries
Maximum extracted size
Maximum single file size
Controlled storage root
```

Archive extraction must never be treated as arbitrary filesystem extraction.

---

## 3. Batch Processing

Processing is executed asynchronously using Spring Batch.

Simplified flow:

```text
Upload accepted
      |
      v
Create processing job
      |
      v
Read extracted CV
      |
      v
Parse document
      |
      v
Convert parsed information
      |
      v
Persist candidate
      |
      +----------------------+
      |                      |
      v                      v
Success                 Processing failure
```

Spring Batch automatic startup is disabled:

```yaml
spring:
  batch:
    job:
      enabled: false
```

Jobs are started explicitly by application logic.

---

## 4. Candidate Persistence

Candidate data is stored in PostgreSQL.

Candidate information can then be:

```text
searched
filtered
sorted
paginated
exported
```

---

# Security

CVScanner is configured as an **OAuth2 Resource Server**.

Clients must send a valid Bearer JWT:

```http
Authorization: Bearer <access-token>
```

JWT verification is configured using:

```yaml
security:
  jwt:
    issuer-uri: ...
    jwk-set-uri: ...
    roles-claim: roles
```

---

## Roles

The application currently uses:

```text
ROLE_RECRUITER
ROLE_ADMIN
```

Business APIs require one of the accepted application roles.

---

## Explicit Endpoint Allowlist

Security uses explicit endpoint authorization.

Allowed business operations include:

```text
POST /api/v1/uploads

GET /api/v1/uploads/{id}

GET /api/v1/uploads/{id}/failures

GET /api/v1/candidates

GET /api/v1/candidates/export.csv

GET /api/v1/candidates/export.xlsx
```

After explicit rules are applied, unmatched requests are denied:

```java
.anyRequest().denyAll()
```

This is intentional.

New endpoints should not accidentally become publicly accessible.

Whenever a new business endpoint is introduced, its authorization rule must also be explicitly reviewed.

---

## Public Health Endpoints

The following endpoints are publicly accessible for infrastructure probes:

```text
/actuator/health
/actuator/health/**
/livez
/readyz
```

Metrics are protected and require administrator authorization:

```text
/actuator/metrics
/actuator/metrics/**
```

---

# Rate Limiting

CVScanner uses distributed Redis-backed rate limiting.

Technology:

```text
Bucket4j
Redis
Lettuce
```

Rate-limit keys are based on:

```text
policy + hashed authenticated principal
```

Raw JWTs and raw principals are not used as Redis keys.

---

## Policies

Three policy groups are currently defined:

```text
UPLOAD
READ
EXPORT
```

Default configuration:

```yaml
app:
  rate-limit:
    enabled: true
    redis-timeout: 500ms
    key-prefix: cvscanner:rate-limit
    fail-open: false

    upload:
      capacity: 5
      refill-tokens: 5
      refill-period: 10m

    read:
      capacity: 60
      refill-tokens: 60
      refill-period: 1m

    export:
      capacity: 10
      refill-tokens: 10
      refill-period: 1m
```

---

## Rate Limit Responses

When a request exceeds its available quota:

```http
HTTP/1.1 429 Too Many Requests
```

Responses can contain:

```text
Retry-After
X-Rate-Limit-Remaining
```

Application error code:

```text
RATE_LIMIT_EXCEEDED
```

---

## Redis Failure Behavior

Rate limiting supports configurable backend failure behavior.

### Fail Closed

```text
fail-open=false
```

Redis unavailable:

```http
HTTP/1.1 503 Service Unavailable
```

Error code:

```text
RATE_LIMIT_BACKEND_UNAVAILABLE
```

This is the production default.

### Fail Open

```text
fail-open=true
```

Requests continue when Redis is temporarily unavailable.

This may be useful in environments where service availability is preferred over strict distributed rate enforcement.

---

# Batch Processing

Batch worker configuration:

```yaml
app:
  batch:
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 100
    await-termination-seconds: 30

    retry:
      max-retries: 2
      delay: 500ms
```

The system includes coverage for:

```text
job processing
retry
restart
failure handling
live upload progress
```

Batch lifecycle should remain explicit.

Avoid enabling automatic execution of all available Spring Batch jobs during application startup.

---

# Upload Storage Cleanup

CV files are stored persistently and cleaned according to retention configuration.

Default configuration:

```yaml
app:
  cleanup:
    enabled: true
    completed-retention: 7d
    batch-size: 100
    scheduler-enabled: false
    schedule-delay: PT1H
    initial-delay: PT1M
```

Cleanup is designed to support:

```text
retention policy
batch deletion
distributed locking
metrics
already-deleted files
safe repeated execution
```

The scheduler can be enabled using environment configuration when desired.

---

# Candidate Search and Export

Candidate API:

```text
GET /api/v1/candidates
```

The API supports validated query parameters, filtering, pagination and sorting.

Invalid input returns standardized client errors rather than exposing internal implementation details.

---

## CSV Export

```text
GET /api/v1/candidates/export.csv
```

Returns candidate data in CSV format.

---

## XLSX Export

```text
GET /api/v1/candidates/export.xlsx
```

Returns candidate data as a Microsoft Excel workbook.

XLSX generation uses Apache POI.

---

# Health and Observability

CVScanner exposes Spring Boot Actuator health information.

---

## Liveness

```text
GET /livez
```

Liveness answers:

> Is the application process alive?

Liveness intentionally does not depend on external infrastructure such as PostgreSQL or Redis.

Configured group:

```yaml
management:
  endpoint:
    health:
      group:
        liveness:
          include:
            - livenessState
```

---

## Readiness

```text
GET /readyz
```

Readiness answers:

> Can this application instance currently serve production traffic correctly?

Readiness includes:

```text
readinessState
database
rate-limit Redis
```

Configuration:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include:
            - readinessState
            - db
            - rateLimitRedis
```

With production fail-closed rate limiting, Redis unavailability makes readiness fail.

---

## Health Privacy

Detailed component health information is not exposed publicly:

```yaml
management:
  endpoint:
    health:
      show-details: never
      show-components: never
```

---

## Metrics

Actuator metrics are exposed internally through:

```text
/actuator/metrics
```

Metrics access requires administrator authorization.

Cleanup-specific metrics are also available through Actuator.

---

## Correlation IDs

HTTP requests are assigned correlation IDs.

They can be used to connect:

```text
incoming HTTP requests
application logs
processing errors
operational incidents
```

HTTP access logs contain information such as:

```text
method
path
status
duration
correlationId
```

Sensitive credentials must never be written into these logs.

---

# Configuration Profiles

CVScanner provides separate Spring profiles.

```text
default/shared
dev
staging
prod
```

Files:

```text
application.yaml
application-dev.yml
application-staging.yml
application-prod.yml
```

---

## Shared Configuration

`application.yaml` contains configuration shared by all environments.

Important shared runtime settings include:

```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate

  flyway:
    enabled: true

  batch:
    job:
      enabled: false

server:
  shutdown: graceful
```

---

## Development Profile

Development profile is intended for local infrastructure.

Typical local dependencies:

```text
PostgreSQL -> localhost:5435
Redis      -> localhost:6385
Identity Provider / Keycloak -> localhost:8180
```

Start application with:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

or configure:

```text
SPRING_PROFILES_ACTIVE=dev
```

---

## Production Profile

Production must use:

```text
SPRING_PROFILES_ACTIVE=prod
```

The production profile intentionally requires real environment configuration.

Production secrets must not fall back to local development credentials.

Missing critical production configuration should prevent successful application startup.

This fail-fast behavior is intentional.

---

# Local Development

## Prerequisites

Install:

```text
Java 21
Maven 3.9+
Docker Desktop
Docker Compose
Git
```

Verify:

```powershell
java -version
mvn -version
docker version
docker compose version
```

---

## Start Local Infrastructure

From the project root:

```powershell
docker compose up -d
```

Verify containers:

```powershell
docker compose ps
```

Default development infrastructure includes PostgreSQL and Redis.

---

## Start Application

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Default application port:

```text
8080
```

If port 8080 is already occupied:

```powershell
$env:SERVER_PORT="8081"

mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

---

## Health Check

```text
http://localhost:8080/livez
http://localhost:8080/readyz
```

Expected:

```http
HTTP/1.1 200 OK
```

when dependencies required for readiness are healthy.

---

# Docker

CVScanner uses a multi-stage Docker build.

Build stage:

```text
maven:3.9-eclipse-temurin-21
```

Runtime stage:

```text
eclipse-temurin:21-jre-jammy
```

The final image does not contain the full Maven build environment.

---

## Build Image

```powershell
docker build -t cvscanner:0.0.1 .
```

---

## Non-Root Runtime

The container runs as:

```text
cvscanner
```

not as `root`.

Verify:

```powershell
docker image inspect `
    cvscanner:0.0.1 `
    --format '{{.Config.User}}'
```

Expected:

```text
cvscanner
```

---

## Runtime Profile

The production image defines:

```text
SPRING_PROFILES_ACTIVE=prod
```

---

## Docker Healthcheck

The image checks:

```text
/readyz
```

A container only becomes healthy when the application is ready to serve traffic.

---

# Production Deployment

Production orchestration is defined in:

```text
docker-compose.prod.yml
```

Production services:

```text
cvscanner
postgres
redis
```

---

## Production Networks

Two networks are used:

```text
backend
edge
```

`backend` is internal.

PostgreSQL and Redis are attached only to the internal backend network.

They are not intended to be publicly exposed.

The CVScanner service also joins the edge network because it may need access to external services such as the JWT issuer / identity provider.

---

## Production Hardening

The application container uses:

```yaml
read_only: true

tmpfs:
  - /tmp

cap_drop:
  - ALL

security_opt:
  - no-new-privileges:true
```

The container also runs as the dedicated non-root `cvscanner` user.

---

## Persistent Production Volumes

Production Compose declares persistent volumes for:

```text
PostgreSQL data
uploaded CV files
```

Example conceptual mapping:

```text
cvscanner-postgres-data
        |
        v
/var/lib/postgresql/data


cvscanner-upload-data
        |
        v
/app/storage/uploads
```

Redis rate-limit data is intentionally treated as transient infrastructure state.

---

## Create Production Environment File

Start from:

```text
.env.prod.example
```

Create:

```text
.env.prod
```

Do not commit this file.

Verify:

```powershell
git check-ignore .env.prod
```

Expected:

```text
.env.prod
```

---

## Validate Compose

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    config `
    --quiet
```

No output indicates successful validation.

---

## Start Production Stack

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    up -d
```

Check status:

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    ps
```

---

## Production Logs

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    logs `
    --tail=200 `
    cvscanner
```

Follow logs:

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    logs `
    -f `
    cvscanner
```

---

# Environment Variables

Critical production configuration:

| Variable | Purpose |
|---|---|
| `CVSCANNER_DB_URL` | PostgreSQL JDBC URL |
| `CVSCANNER_DB_USERNAME` | PostgreSQL username |
| `CVSCANNER_DB_PASSWORD` | PostgreSQL password |
| `CVSCANNER_JWT_ISSUER_URI` | JWT issuer |
| `CVSCANNER_JWT_JWK_SET_URI` | JWK endpoint |
| `CVSCANNER_JWT_ROLES_CLAIM` | JWT role claim |
| `CVSCANNER_RATE_LIMIT_REDIS_URI` | Redis connection URI |
| `CVSCANNER_RATE_LIMIT_FAIL_OPEN` | Redis failure policy |
| `CVSCANNER_STORAGE_ROOT` | Persistent upload storage |
| `CVSCANNER_HTTP_PORT` | Host HTTP port |
| `CVSCANNER_IMAGE_TAG` | Docker image version |

---

## Database Pool

Optional production Hikari configuration:

```text
CVSCANNER_DB_MAX_POOL_SIZE
CVSCANNER_DB_MIN_IDLE
CVSCANNER_DB_CONNECTION_TIMEOUT_MS
CVSCANNER_DB_VALIDATION_TIMEOUT_MS
```

Typical production defaults:

```text
maximum-pool-size: 10
minimum-idle: 2
connection-timeout: 5000 ms
validation-timeout: 3000 ms
```

---

## Rate Limit

Available configuration includes:

```text
CVSCANNER_RATE_LIMIT_ENABLED
CVSCANNER_RATE_LIMIT_REDIS_URI
CVSCANNER_RATE_LIMIT_REDIS_TIMEOUT
CVSCANNER_RATE_LIMIT_KEY_PREFIX
CVSCANNER_RATE_LIMIT_FAIL_OPEN
```

Individual policy settings can also be externally configured.

---

## Cleanup

```text
CVSCANNER_CLEANUP_ENABLED
CVSCANNER_COMPLETED_RETENTION
CVSCANNER_CLEANUP_BATCH_SIZE
CVSCANNER_CLEANUP_SCHEDULER_ENABLED
CVSCANNER_CLEANUP_SCHEDULE_DELAY
CVSCANNER_CLEANUP_INITIAL_DELAY
```

---

# Testing

CVScanner separates unit and integration test execution.

---

## Full Verification

Run:

```powershell
mvn clean verify
```

The release-tested baseline currently completes:

```text
Tests run: 117
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

---

## Unit Tests

Unit tests are executed by Maven Surefire.

```text
maven-surefire-plugin
```

---

## Integration Tests

Integration tests are executed by Maven Failsafe.

```text
maven-failsafe-plugin
```

Integration tests include real infrastructure through Testcontainers where required.

Covered areas include:

```text
PostgreSQL
Redis
security
JWT
RBAC
rate limiting
readiness
upload
batch processing
restart
cleanup
candidate search
CSV export
XLSX export
error contracts
```

---

## Test JVM Isolation

Integration tests use controlled fork settings to avoid native resource accumulation across a large number of Spring/Testcontainers application contexts.

The integration-test JVM is constrained with:

```text
-Xmx512m
-XX:MaxMetaspaceSize=256m
```

Failsafe runs:

```text
forkCount = 1
reuseForks = false
```

Mockito is loaded explicitly as a Java agent for Java 21 compatibility.

---

# Release Gate

Final release verification is automated by:

```text
scripts/release-gate.ps1
```

The gate validates:

```text
required files
production env Git protection
Maven verification
production Docker build
non-root runtime user
production Spring profile
Docker Compose configuration
production-like stack startup
container health
liveness
readiness
```

---

## Execute Final Release Gate

```powershell
powershell `
    -NoProfile `
    -ExecutionPolicy Bypass `
    -File .\scripts\release-gate.ps1 `
    -ImageTag "0.0.1" `
    -Port 8080
```

Successful release:

```text
===============================================
 RELEASE GATE PASSED
===============================================
```

A release should not be considered ready if this gate fails.

---

# Production Smoke Test

Standalone health verification is available through:

```text
scripts/production-smoke.ps1
```

Run:

```powershell
powershell `
    -NoProfile `
    -ExecutionPolicy Bypass `
    -File .\scripts\production-smoke.ps1 `
    -Port 8080
```

It verifies:

```text
/livez
/readyz
```

---

# Database Migrations

Flyway manages database schema changes.

Migration location:

```text
src/main/resources/db/migration
```

Flyway is enabled:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Hibernate schema generation is not used in production.

Instead:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

This means Hibernate validates the mapped schema but does not silently generate production database changes.

---

## Migration Rule

Existing migrations should be treated as immutable after release.

Do not modify an already deployed migration.

Instead add:

```text
V7__description.sql
V8__description.sql
...
```

depending on the current schema version.

---

# Persistent Data

There are two major categories of persistent business data.

## PostgreSQL

Contains application records such as:

```text
uploads
candidates
candidate skills
processing failures
cleanup metadata
Spring Batch metadata
```

PostgreSQL data must be backed up according to the environment's recovery policy.

---

## CV Upload Storage

Uploaded/extracted CV content is stored under:

```text
/app/storage/uploads
```

in production.

The production container mounts persistent storage at this path.

This volume must not be removed during a normal deployment.

---

## Redis

Redis currently stores distributed rate-limit state.

This state is transient.

Losing Redis rate-limit buckets does not destroy candidate or upload business data.

---

# Rollback

Rollback should prefer application image rollback rather than destructive infrastructure changes.

Assume:

```text
current image:
cvscanner:0.0.2

previous known-good image:
cvscanner:0.0.1
```

Update the configured image tag:

```dotenv
CVSCANNER_IMAGE_TAG=0.0.1
```

Then redeploy:

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    up -d
```

Verify:

```text
/livez
/readyz
```

and application logs.

---

## Important Database Warning

Application rollback and database rollback are not the same operation.

A newer Flyway migration may make an older application version incompatible with the current database schema.

Before deploying schema-changing releases:

```text
review migration compatibility
take an appropriate database backup
define rollback expectations
verify older application compatibility when rollback is required
```

Do not manually delete Flyway migration history as a rollback mechanism.

---

# Graceful Shutdown

The application enables graceful shutdown:

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

Production Compose uses a stop grace period larger than the application shutdown window.

This gives active work time to terminate cleanly before Docker forcefully stops the process.

---

# Operational Notes

## If readiness returns 503

Check:

```text
PostgreSQL connectivity
Redis connectivity
rateLimitRedis health
application startup logs
Flyway migration state
```

Retrieve logs:

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    logs `
    --tail=200 `
    cvscanner
```

---

## If liveness returns 503

This normally indicates a deeper application lifecycle problem rather than only an external dependency issue.

Check the application process and container logs.

---

## If PostgreSQL is unavailable

With database readiness included:

```text
/readyz -> DOWN
```

The instance should not receive normal production traffic until PostgreSQL becomes healthy again.

---

## If Redis is unavailable

With:

```text
CVSCANNER_RATE_LIMIT_FAIL_OPEN=false
```

readiness becomes unhealthy because strict distributed rate limiting cannot currently be guaranteed.

---

## If port 8080 is occupied

Configure another host port:

```dotenv
CVSCANNER_HTTP_PORT=8081
```

Then run health checks against:

```text
http://localhost:8081
```

---

# Known Architectural Limitations

## Local Persistent Upload Storage

The current production Compose architecture persists CV files using a Docker volume.

This is suitable for:

```text
single-host
single-application-instance
controlled deployment
```

It is not sufficient by itself for arbitrary horizontal scaling across multiple machines.

For multi-node production deployment, CV storage should move to shared durable storage such as:

```text
S3-compatible object storage
cloud object storage
shared filesystem designed for the deployment architecture
```

---

## Horizontal Scaling

Redis-backed rate limiting is already distributed.

PostgreSQL is already externally shared.

However upload files must also become shared before multiple application replicas can safely process the same persistent upload namespace.

---

## Identity Provider

CVScanner validates JWTs but does not itself act as the user identity provider.

Authentication is expected to be handled by an external OAuth2 / OpenID Connect compatible identity system.

---

# Production Readiness Status

The current release baseline has been validated with:

```text
Java 21
Spring Boot 4.1
PostgreSQL 16
Redis 7
Docker Desktop
Docker Compose
Testcontainers
```

Final validation result:

```text
Maven verification       PASS
117 tests                PASS
Docker image build       PASS
Non-root runtime         PASS
Production profile       PASS
Compose validation       PASS
Container health         PASS
Liveness                 PASS
Readiness                PASS
Release gate             PASS
```

---

# Development Principles

When extending CVScanner:

1. Add database changes through new Flyway migrations.
2. Add authorization rules explicitly for every new endpoint.
3. Do not expose internal Actuator details publicly.
4. Do not log JWTs, secrets or raw credentials.
5. Keep production configuration environment-driven.
6. Add integration tests for infrastructure-dependent behavior.
7. Preserve readiness semantics.
8. Keep business data out of Redis unless intentionally redesigned.
9. Keep upload extraction constrained.
10. Run `mvn clean verify` before release.
11. Run the production release gate before declaring a release ready.

---

# Build

```powershell
mvn clean package
```

Skip tests only for controlled image packaging after tests have already passed:

```powershell
mvn clean package -DskipTests
```

For release verification always use:

```powershell
mvn clean verify
```

---

# Quick Start

```powershell
# Start development infrastructure
docker compose up -d

# Run application
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"

# Liveness
Invoke-WebRequest http://localhost:8080/livez

# Readiness
Invoke-WebRequest http://localhost:8080/readyz
```

---

# Final Release Command

```powershell
powershell `
    -NoProfile `
    -ExecutionPolicy Bypass `
    -File .\scripts\release-gate.ps1 `
    -ImageTag "0.0.1" `
    -Port 8080
```

Expected result:

```text
RELEASE GATE PASSED
```