# CVScanner

[![CI](https://github.com/Seyidli06/CVScanner/actions/workflows/ci.yml/badge.svg)](https://github.com/Seyidli06/CVScanner/actions/workflows/ci.yml)

Production-oriented backend for **bulk CV ingestion, parsing, candidate extraction, search and export**, built with **Java 21** and **Spring Boot 4**.

CVScanner accepts ZIP archives containing PDF/DOCX resumes, processes them asynchronously with Spring Batch, extracts structured candidate information, persists results in PostgreSQL, exposes searchable REST APIs, supports CSV/XLSX exports, and includes production-oriented security, distributed rate limiting, observability, cleanup, Docker deployment and CI verification.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Processing Flow](#processing-flow)
- [REST API](#rest-api)
- [API Documentation](#api-documentation)
- [Security](#security)
- [Rate Limiting](#rate-limiting)
- [Batch Processing](#batch-processing)
- [Upload Safety](#upload-safety)
- [Storage Cleanup](#storage-cleanup)
- [Health and Observability](#health-and-observability)
- [Configuration Profiles](#configuration-profiles)
- [Local Development](#local-development)
- [Testing](#testing)
- [Continuous Integration](#continuous-integration)
- [Docker](#docker)
- [Production Deployment](#production-deployment)
- [Environment Variables](#environment-variables)
- [Database Migrations](#database-migrations)
- [Release Gate](#release-gate)
- [Rollback](#rollback)
- [Known Architectural Limitations](#known-architectural-limitations)
- [Development Principles](#development-principles)

---

# Overview

CVScanner simulates a real-world HR automation platform where recruiters may need to process hundreds or thousands of candidate resumes without manually opening every document.

The core workflow is:

```text
ZIP Upload
    |
    v
Upload Validation
    |
    v
Safe ZIP Extraction
    |
    v
Spring Batch Job
    |
    v
PDF / DOCX Parsing
    |
    v
Candidate Data Extraction
    |
    v
PostgreSQL Persistence
    |
    +------------------------+
    |                        |
    v                        v
Candidate Search       Processing Failures
    |
    +------------------------+
    |                        |
    v                        v
 CSV Export             XLSX Export
```

The project goes beyond the basic functional requirements and includes infrastructure and production-hardening concerns such as JWT authentication, distributed Redis-backed rate limiting, health checks, graceful shutdown, production profiles, persistent cleanup, Docker hardening, automated testing and GitHub Actions CI.

---

# Key Features

## CV Processing

- Bulk CV upload using ZIP archives
- PDF and DOCX document parsing
- Apache Tika based text extraction
- Rule-based candidate information extraction
- Spring Batch processing pipeline
- Asynchronous job execution
- Retry and skip handling
- Failed document tracking
- Restartable batch jobs
- Upload progress tracking

## Candidate Data

- Candidate persistence in PostgreSQL
- Candidate skills
- Years of experience
- Job type
- Location
- Search and filtering
- Pagination
- Sorting
- CSV export
- XLSX export

## Security

- OAuth2 Resource Server
- Bearer JWT authentication
- JWT issuer validation
- JWK-based signature verification
- Role-based authorization
- `ROLE_RECRUITER`
- `ROLE_ADMIN`
- Explicit endpoint allowlisting
- Default deny-all security policy

## Infrastructure

- PostgreSQL
- Redis
- Flyway
- HikariCP
- Docker Compose
- Testcontainers
- Persistent upload storage
- Distributed rate limiting
- Distributed cleanup locking

## Production / Operations

- Liveness probe
- Readiness probe
- Actuator metrics
- Correlation IDs
- HTTP access logging
- Graceful shutdown
- Production fail-fast configuration
- Non-root Docker container
- Read-only production filesystem
- Linux capability dropping
- Production smoke test
- Automated release gate
- GitHub Actions CI

## API Documentation

- OpenAPI 3
- Swagger UI
- Bearer JWT documentation
- Swagger enabled for development/staging
- Swagger disabled in production

---

# Architecture

High-level runtime architecture:

```text
                           Client
                             |
                             | Bearer JWT
                             v
                 +-------------------------+
                 |    Spring Security      |
                 | OAuth2 Resource Server  |
                 | JWT + RBAC              |
                 +------------+------------+
                              |
                              v
                 +-------------------------+
                 | Distributed Rate Limit  |
                 | Bucket4j + Redis        |
                 +------------+------------+
                              |
                              v
              +------------------------------------+
              |             CVScanner              |
              |                                    |
              | Upload API                         |
              | Candidate API                      |
              | Export API                         |
              | Processing Failure API             |
              | Spring Batch                       |
              | Cleanup Scheduler                  |
              | Health / Metrics                   |
              +----------+-------------------------+
                         |
             +-----------+------------+
             |                        |
             v                        v
    +-----------------+       +-----------------+
    |   PostgreSQL    |       |      Redis      |
    |                 |       |                 |
    | Candidates      |       | Rate limits     |
    | Uploads         |       |                 |
    | Failures        |       +-----------------+
    | Batch metadata  |
    | Cleanup records |
    +--------+--------+
             |
             v
    +----------------------+
    | Persistent CV Files  |
    +----------------------+
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
Spring Validation
```

## Database

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
Bucket4j 8.19
```

## Document Processing

```text
Apache Tika
Apache POI
```

## API Documentation

```text
OpenAPI 3
springdoc-openapi
Swagger UI
```

## Testing

```text
JUnit 5
Spring Boot Test
MockMvc
Mockito
Spring Security Test
Spring Batch Test
Testcontainers
Maven Surefire
Maven Failsafe
```

## Deployment

```text
Docker
Docker Compose
Eclipse Temurin JRE 21
Maven Wrapper
GitHub Actions
PowerShell release tooling
```

---

# Project Structure

Representative project structure:

```text
CVScanner/
│
├── .github/
│   └── workflows/
│       └── ci.yml
│
├── scripts/
│   ├── production-smoke.ps1
│   └── release-gate.ps1
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
├── .dockerignore
├── .env.prod.example
├── .gitignore
├── Dockerfile
├── docker-compose.yml
├── docker-compose.prod.yml
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

---

# Processing Flow

## 1. Upload

A recruiter or administrator submits a ZIP archive:

```http
POST /api/v1/uploads
```

The upload is validated before processing starts.

---

## 2. Safe Extraction

The ZIP archive is extracted into controlled application storage.

The extraction layer enforces limits for:

```text
Maximum archive entries
Maximum total extracted size
Maximum single file size
Controlled extraction root
```

Default limits:

```yaml
app:
  upload:
    storage-root: ./storage/uploads
    max-entries: 5000
    max-extracted-size: 1GB
    max-single-file-size: 25MB
```

---

## 3. Batch Job

Spring Batch processes discovered CV files.

```text
Reader
  |
  v
Document Parser
  |
  v
Candidate Extractor
  |
  v
Writer
  |
  v
PostgreSQL
```

Jobs are launched explicitly by application logic.

Automatic execution of every Spring Batch job during startup is disabled:

```yaml
spring:
  batch:
    job:
      enabled: false
```

---

## 4. Document Parsing

Apache Tika extracts text from supported documents such as:

```text
PDF
DOCX
```

The extracted text is passed to candidate extraction logic.

---

## 5. Candidate Extraction

The processing pipeline extracts structured candidate information such as:

```text
Full name
Skills
Years of experience
Location
Preferred job type
```

---

## 6. Persistence

Candidate information is persisted using PostgreSQL and Spring Data JPA.

Database schema changes are managed exclusively through Flyway migrations.

---

# REST API

Main business endpoints:

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/uploads` | Upload a ZIP archive containing CVs |
| `GET` | `/api/v1/uploads/{id}` | Retrieve upload and processing status |
| `GET` | `/api/v1/uploads/{id}/failures` | Retrieve CV processing failures |
| `GET` | `/api/v1/candidates` | Search, filter and paginate candidates |
| `GET` | `/api/v1/candidates/export.csv` | Export candidate results as CSV |
| `GET` | `/api/v1/candidates/export.xlsx` | Export candidate results as XLSX |

Business endpoints require an authenticated user with an accepted application role.

---

# API Documentation

CVScanner provides **OpenAPI 3 documentation** through springdoc-openapi.

Development Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

Swagger includes an HTTP Bearer security scheme:

```text
bearerAuth
```

A JWT can be supplied through the Swagger **Authorize** button when testing protected API operations.

---

## Environment Policy

Swagger is enabled by default for development and staging environments.

Production disables both API documentation endpoints and Swagger UI:

```yaml
springdoc:
  api-docs:
    enabled: false

  swagger-ui:
    enabled: false
```

This keeps interactive documentation available to developers while avoiding an unnecessary public documentation surface in production.

---

# Security

CVScanner acts as an OAuth2 Resource Server.

Clients authenticate using:

```http
Authorization: Bearer <JWT>
```

JWT validation uses:

```yaml
security:
  jwt:
    issuer-uri: ...
    jwk-set-uri: ...
    roles-claim: roles
```

---

## Roles

Currently supported application roles:

```text
ROLE_RECRUITER
ROLE_ADMIN
```

---

## Security Policy

Business endpoints are explicitly allowlisted.

Any unmatched request is denied:

```java
.anyRequest()
.denyAll()
```

This means adding a new controller does not automatically expose it.

Every new endpoint must receive an explicit security decision.

---

## Public Endpoints

Infrastructure health endpoints are intentionally public:

```text
/actuator/health
/actuator/health/**
/livez
/readyz
```

Swagger endpoints are available only when springdoc is enabled.

---

## Protected Metrics

Actuator metrics require:

```text
ROLE_ADMIN
```

Endpoints:

```text
/actuator/metrics
/actuator/metrics/**
```

---

# Rate Limiting

CVScanner uses distributed rate limiting backed by Redis.

Components:

```text
Bucket4j
Lettuce
Redis
```

Rate-limit state is shared between application instances.

Keys use a hashed authenticated principal rather than storing raw usernames or JWTs.

Conceptually:

```text
cvscanner:rate-limit:<policy>:<principal-hash>
```

---

## Rate Limit Policies

Three policy categories exist:

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

## Rate Limit Response

When the quota is exceeded:

```http
HTTP/1.1 429 Too Many Requests
```

Relevant response headers may include:

```text
Retry-After
X-Rate-Limit-Remaining
```

Application error code:

```text
RATE_LIMIT_EXCEEDED
```

---

## Redis Failure Policy

### Fail Closed

Production default:

```text
fail-open=false
```

If Redis is unavailable:

```http
HTTP/1.1 503 Service Unavailable
```

Error:

```text
RATE_LIMIT_BACKEND_UNAVAILABLE
```

Readiness also becomes unhealthy.

### Fail Open

With:

```text
fail-open=true
```

requests continue when the Redis rate-limit backend is unavailable.

---

# Batch Processing

Default executor configuration:

```yaml
app:
  batch:
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 100
    await-termination-seconds: 30
```

Retry configuration:

```yaml
app:
  batch:
    retry:
      max-retries: 2
      delay: 500ms
```

The batch implementation includes coverage for:

```text
Normal processing
Retry
Skip
Failure persistence
Job restart
Progress updates
Candidate persistence
```

---

# Upload Safety

Processing arbitrary ZIP archives requires defensive extraction.

CVScanner limits:

```text
Archive entry count
Total extracted size
Individual file size
Extraction location
```

Default values:

```text
Maximum entries:             5000
Maximum extracted size:     1 GB
Maximum single file size:   25 MB
```

Document parsing additionally limits extracted text length:

```yaml
app:
  parsing:
    max-text-length: 1000000
```

---

# Storage Cleanup

CV files are retained for a configured period and can later be removed by the cleanup subsystem.

Defaults:

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

Cleanup behavior includes:

```text
Retention policy
Batch deletion
Persistent cleanup state
Distributed PostgreSQL locking
Idempotent handling
Cleanup metrics
```

The scheduler is disabled by default and can be activated through configuration.

---

# Health and Observability

CVScanner exposes infrastructure-friendly health endpoints.

---

## Liveness

```text
GET /livez
```

Liveness represents whether the application process itself is alive.

External dependencies are intentionally excluded from the liveness group.

Configuration:

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

Readiness represents whether the application should currently receive traffic.

Dependencies include:

```text
Spring readiness state
PostgreSQL
Rate-limit Redis
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

---

## Health Information Privacy

Detailed infrastructure information is not exposed:

```yaml
management:
  endpoint:
    health:
      show-details: never
      show-components: never
```

---

## Metrics

Metrics are available through Spring Boot Actuator:

```text
/actuator/metrics
```

Access requires administrator authorization.

---

## Correlation IDs

Incoming HTTP requests receive correlation identifiers.

They help connect:

```text
HTTP request
Application log
Processing event
Failure
Operational incident
```

Sensitive credentials and JWT values must never be logged.

---

# Configuration Profiles

CVScanner separates runtime configuration by environment.

Files:

```text
application.yaml
application-dev.yml
application-staging.yml
application-prod.yml
```

Profiles:

```text
dev
staging
prod
```

---

## Shared Runtime Configuration

Important shared settings:

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

## Development

Typical development infrastructure:

```text
PostgreSQL     localhost:5435
Redis          localhost:6385
Identity       localhost:8180
Application    localhost:8080
```

---

## Production

Production uses:

```text
SPRING_PROFILES_ACTIVE=prod
```

Production configuration intentionally requires environment-provided credentials and connection information.

Critical missing configuration prevents startup.

This fail-fast behavior avoids silently falling back to insecure development values.

---

# Local Development

## Requirements

Install:

```text
Java 21
Docker Desktop
Docker Compose
Git
```

Maven installation is optional because the repository includes Maven Wrapper.

Verify:

```powershell
java -version
docker version
docker compose version
```

---

## Start Infrastructure

From the repository root:

```powershell
docker compose up -d
```

Check:

```powershell
docker compose ps
```

---

## Run CVScanner

Windows:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Alternatively:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

---

## Health

```powershell
Invoke-WebRequest http://localhost:8080/livez
Invoke-WebRequest http://localhost:8080/readyz
```

When required dependencies are healthy:

```text
HTTP 200
```

---

## Swagger

```text
http://localhost:8080/swagger-ui.html
```

---

## Different Port

If `8080` is already occupied:

```powershell
$env:SERVER_PORT="8081"

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Then:

```text
http://localhost:8081/swagger-ui.html
http://localhost:8081/livez
http://localhost:8081/readyz
```

---

# Testing

The project uses separate Maven test phases.

---

## Full Verification

Run:

```powershell
.\mvnw.cmd clean verify
```

or:

```powershell
mvn clean verify
```

Current validated baseline:

```text
Tests run: 119
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

---

## Unit Tests

Unit tests run through:

```text
Maven Surefire
```

---

## Integration Tests

Integration tests run through:

```text
Maven Failsafe
```

The integration suite covers areas including:

```text
PostgreSQL
Redis
Testcontainers
JWT
RBAC
Security access matrix
Rate limiting
Distributed rate-limit state
Redis failure behavior
Liveness/readiness
Upload handling
ZIP extraction
Spring Batch
Retry
Restart
Candidate persistence
Candidate search
CSV export
XLSX export
Cleanup
Metrics
OpenAPI
Swagger
Error contracts
```

---

## Testcontainers

Infrastructure-dependent tests use real disposable containers.

For example:

```text
PostgreSQLContainer
Redis container
```

This reduces dependency on developer-machine state and allows the same integration tests to run inside GitHub Actions.

---

## JVM Test Isolation

Integration tests use:

```text
forkCount = 1
reuseForks = false
```

with:

```text
-Xmx512m
-XX:MaxMetaspaceSize=256m
```

This prevents excessive native-memory accumulation across large Spring/Testcontainers test suites.

Mockito is loaded explicitly as a Java agent for Java 21 compatibility.

---

# Continuous Integration

GitHub Actions provides automated Continuous Integration.

Workflow:

```text
.github/workflows/ci.yml
```

CI executes for:

```text
Pushes to main
Pull requests targeting main
```

---

## Pipeline

```text
Git Push
   |
   v
Maven Verify
   |
   v
Docker Build
   |
   v
CI PASS
```

---

## Maven Verification

GitHub Actions creates a clean Ubuntu environment using Java 21 and executes:

```bash
./mvnw -B -ntp clean verify
```

This runs both unit and integration tests.

Testcontainers use Docker available on the GitHub Actions runner.

If verification fails, Surefire and Failsafe reports are uploaded as workflow artifacts.

---

## Docker Build

Docker image verification executes only after Maven verification succeeds:

```bash
docker build \
  --tag cvscanner:ci \
  .
```

This ensures committed source code can produce the production image.

---

## CI Status

The badge at the top of this README reflects the current state of the workflow on the `main` branch.

A green badge indicates that the current committed version has passed the automated CI pipeline.

---

# Docker

CVScanner uses a multi-stage Docker build.

---

## Build Stage

```text
maven:3.9-eclipse-temurin-21
```

The application is compiled and packaged here.

---

## Runtime Stage

```text
eclipse-temurin:21-jre-jammy
```

The Maven build environment is not included in the final runtime image.

---

## Build

```powershell
docker build -t cvscanner:0.0.1 .
```

---

## Non-Root Runtime

The application runs as:

```text
cvscanner
```

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

## Production Profile

Docker image default:

```text
SPRING_PROFILES_ACTIVE=prod
```

---

## Healthcheck

Docker checks:

```text
http://127.0.0.1:8080/readyz
```

The container becomes healthy only when the application is ready.

---

# Production Deployment

Production infrastructure is defined in:

```text
docker-compose.prod.yml
```

Services:

```text
cvscanner
postgres
redis
```

---

## Networks

```text
backend
edge
```

`backend` is configured as an internal network.

PostgreSQL and Redis are not published directly to the host.

The application joins both `backend` and `edge`.

---

## Container Hardening

The production application container uses:

```yaml
read_only: true

tmpfs:
  - /tmp

cap_drop:
  - ALL

security_opt:
  - no-new-privileges:true
```

It also runs as a dedicated non-root user.

---

## Persistent Volumes

Production persists:

```text
PostgreSQL database data
Uploaded CV files
```

Conceptually:

```text
PostgreSQL
    |
    v
cvscanner-postgres-data


Uploads
    |
    v
cvscanner-upload-data
```

Redis rate-limit state is intentionally transient.

---

## Production Environment

Copy:

```text
.env.prod.example
```

to:

```text
.env.prod
```

Fill in the real environment configuration.

`.env.prod` must never be committed.

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

---

## Start

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    up -d
```

---

## Status

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    ps
```

---

## Logs

```powershell
docker compose `
    --env-file .\.env.prod `
    -f .\docker-compose.prod.yml `
    logs `
    --tail=200 `
    cvscanner
```

Follow:

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

Important production variables:

| Variable | Purpose |
|---|---|
| `CVSCANNER_IMAGE_TAG` | Docker image version |
| `CVSCANNER_HTTP_PORT` | Host HTTP port |
| `CVSCANNER_DB_NAME` | PostgreSQL database |
| `CVSCANNER_DB_URL` | JDBC URL when configuring the app directly |
| `CVSCANNER_DB_USERNAME` | PostgreSQL username |
| `CVSCANNER_DB_PASSWORD` | PostgreSQL password |
| `CVSCANNER_JWT_ISSUER_URI` | OAuth2/OIDC issuer |
| `CVSCANNER_JWT_JWK_SET_URI` | JWT JWK endpoint |
| `CVSCANNER_JWT_ROLES_CLAIM` | JWT role claim |
| `CVSCANNER_RATE_LIMIT_REDIS_URI` | Redis connection URI |
| `CVSCANNER_RATE_LIMIT_FAIL_OPEN` | Rate-limit Redis failure behavior |
| `CVSCANNER_STORAGE_ROOT` | CV storage path |

---

## HikariCP

Optional variables:

```text
CVSCANNER_DB_MAX_POOL_SIZE
CVSCANNER_DB_MIN_IDLE
CVSCANNER_DB_CONNECTION_TIMEOUT_MS
CVSCANNER_DB_VALIDATION_TIMEOUT_MS
```

Production defaults:

```text
maximum-pool-size: 10
minimum-idle: 2
connection-timeout: 5000 ms
validation-timeout: 3000 ms
```

---

## Cleanup

Available cleanup variables include:

```text
CVSCANNER_CLEANUP_ENABLED
CVSCANNER_COMPLETED_RETENTION
CVSCANNER_CLEANUP_BATCH_SIZE
CVSCANNER_CLEANUP_SCHEDULER_ENABLED
CVSCANNER_CLEANUP_SCHEDULE_DELAY
CVSCANNER_CLEANUP_INITIAL_DELAY
```

---

## Rate Limiting

Available settings include:

```text
CVSCANNER_RATE_LIMIT_ENABLED
CVSCANNER_RATE_LIMIT_REDIS_URI
CVSCANNER_RATE_LIMIT_REDIS_TIMEOUT
CVSCANNER_RATE_LIMIT_KEY_PREFIX
CVSCANNER_RATE_LIMIT_FAIL_OPEN
```

Policy-specific capacity and refill values can also be configured externally.

---

# Database Migrations

Database versioning is managed with Flyway.

Location:

```text
src/main/resources/db/migration
```

Current migrations cover:

```text
CV uploads
Candidates
Candidate skills
Processing failures
Candidate source uniqueness
Upload cleanup state
```

Flyway runs during application startup.

---

## Hibernate Policy

Production uses:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Hibernate validates mappings against the schema but does not automatically mutate production database structures.

---

## Migration Rule

Never edit an already deployed Flyway migration.

For new schema changes, add a new version:

```text
V7__description.sql
V8__description.sql
V9__description.sql
```

---

# Release Gate

Production release verification is automated by:

```text
scripts/release-gate.ps1
```

The gate checks:

```text
Required production files
.env.prod Git protection
Maven unit tests
Maven integration tests
Docker image build
Non-root runtime
Production Spring profile
Docker Compose configuration
Production-like startup
Container health
Liveness
Readiness
```

---

## Run Release Gate

```powershell
powershell `
    -NoProfile `
    -ExecutionPolicy Bypass `
    -File .\scripts\release-gate.ps1 `
    -ImageTag "0.0.1" `
    -Port 8080
```

Successful output:

```text
===============================================
 RELEASE GATE PASSED
===============================================
```

---

# Production Smoke Test

Standalone production health verification:

```powershell
powershell `
    -NoProfile `
    -ExecutionPolicy Bypass `
    -File .\scripts\production-smoke.ps1 `
    -Port 8080
```

It checks:

```text
/livez
/readyz
```

---

# Graceful Shutdown

CVScanner enables graceful shutdown:

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

Production Compose provides a larger container stop grace period:

```text
40 seconds
```

This gives the application time to complete shutdown work before Docker terminates the process.

---

# Rollback

Application rollback should use a previously known-good Docker image.

Example:

```text
Current:
cvscanner:0.0.2

Previous:
cvscanner:0.0.1
```

Update:

```dotenv
CVSCANNER_IMAGE_TAG=0.0.1
```

Then:

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

---

## Database Rollback Warning

Application rollback does not automatically mean database rollback.

A newer Flyway migration may be incompatible with an older application image.

Before schema-changing deployments:

```text
Review migration compatibility
Back up PostgreSQL
Define rollback expectations
Keep migrations immutable
Prefer backward-compatible schema changes
```

Never manually remove Flyway history entries as a rollback strategy.

---

# Known Architectural Limitations

## Local Upload Storage

Production currently stores uploaded files in a Docker volume:

```text
/app/storage/uploads
```

This works well for:

```text
Single host
Single application replica
Controlled deployment
```

It is not enough for unrestricted multi-host horizontal scaling.

---

## Horizontal Scaling

These parts already support distributed deployment:

```text
PostgreSQL
Redis-backed rate limiting
```

Uploaded CV storage does not yet provide shared multi-node storage.

For multi-replica deployment, migrate CV storage to something like:

```text
Amazon S3
MinIO
Azure Blob Storage
Google Cloud Storage
Shared durable filesystem
```

---

## Identity Provider

CVScanner does not implement user login itself.

It is an OAuth2 Resource Server and expects JWTs issued by an external identity provider such as:

```text
Keycloak
Auth0
Okta
Azure Entra ID
other OIDC-compatible providers
```

---

## Email Notifications

Email notification after batch completion was an optional requirement and is not currently implemented.

It can be added later without changing the core processing architecture.

---

# Production Readiness

The current release baseline has been validated with:

```text
Java 21
Spring Boot 4.1
PostgreSQL 16
Redis 7
Testcontainers
OpenAPI / Swagger
Docker
Docker Compose
GitHub Actions
```

Verification status:

```text
Maven verification       PASS
119 tests                PASS
OpenAPI integration      PASS
GitHub Actions CI        PASS
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

1. Add database changes only through new Flyway migrations.
2. Explicitly authorize every new HTTP endpoint.
3. Keep unmatched endpoints denied by default.
4. Never log JWTs, credentials or secrets.
5. Keep production configuration environment-driven.
6. Add integration tests for infrastructure-dependent behavior.
7. Preserve liveness/readiness semantics.
8. Keep archive extraction bounded and defensive.
9. Do not store permanent business data in Redis without an explicit design decision.
10. Prefer backward-compatible database migrations.
11. Run `mvn clean verify` before release.
12. Keep CI green.
13. Run the production release gate before deploying.

---

# Quick Start

```powershell
# Clone
git clone https://github.com/Seyidli06/CVScanner.git

cd CVScanner

# Start development infrastructure
docker compose up -d

# Run application
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

Liveness:

```text
http://localhost:8080/livez
```

Readiness:

```text
http://localhost:8080/readyz
```

---

# Build

Package:

```powershell
.\mvnw.cmd clean package
```

Full verification:

```powershell
.\mvnw.cmd clean verify
```

Production Docker image:

```powershell
docker build -t cvscanner:0.0.1 .
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

Expected:

```text
RELEASE GATE PASSED
```

---

## Repository

GitHub:

https://github.com/Seyidli06/CVScanner
