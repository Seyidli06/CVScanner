param(
    [string]$ImageTag = "0.0.1",
    [int]$Port = 8080,
    [string]$EnvFile = ".env.prod",
    [string]$ComposeFile = "docker-compose.prod.yml"
)

$ErrorActionPreference = "Stop"

$ImageName = "cvscanner:$ImageTag"

Write-Host ""
Write-Host "==============================================="
Write-Host " CVScanner Final Production Release Gate"
Write-Host "==============================================="
Write-Host ""

function Step {
    param(
        [string]$Message
    )

    Write-Host ""
    Write-Host ">>> $Message"
    Write-Host ""
}

function Fail {
    param(
        [string]$Message
    )

    Write-Host ""
    Write-Host "RELEASE GATE FAILED"
    Write-Host $Message
    Write-Host ""

    exit 1
}


# ================================================================
# 1. REQUIRED FILES
# ================================================================

Step "Checking required production files"

$requiredFiles = @(
    "pom.xml",
    "Dockerfile",
    ".dockerignore",
    $ComposeFile,
    $EnvFile,
    "scripts\production-smoke.ps1"
)

foreach ($file in $requiredFiles) {

    if (-not (Test-Path $file)) {

        Fail "Required file not found: $file"
    }

    Write-Host "FOUND: $file"
}


# ================================================================
# 2. ENVIRONMENT SECRET SAFETY
# ================================================================

Step "Checking production env Git protection"

git check-ignore $EnvFile | Out-Null

if ($LASTEXITCODE -ne 0) {

    Fail "$EnvFile is NOT ignored by Git."
}

Write-Host "PASS: $EnvFile is ignored by Git"


# ================================================================
# 3. MAVEN VERIFICATION
# ================================================================

Step "Running Maven verification"

mvn clean verify

if ($LASTEXITCODE -ne 0) {

    Fail "mvn clean verify failed."
}

Write-Host "PASS: Maven verification"


# ================================================================
# 4. BUILD PRODUCTION IMAGE
# ================================================================

Step "Building production Docker image: $ImageName"

docker build `
    -t $ImageName `
    .

if ($LASTEXITCODE -ne 0) {

    Fail "Docker image build failed."
}

Write-Host "PASS: Docker image built"


# ================================================================
# 5. VERIFY IMAGE USER
# ================================================================

Step "Checking non-root runtime user"

$runtimeUser =
    docker image inspect `
        $ImageName `
        --format '{{.Config.User}}'

if ($LASTEXITCODE -ne 0) {

    Fail "Unable to inspect Docker image."
}

if ($runtimeUser.Trim() -ne "cvscanner") {

    Fail "Unexpected Docker runtime user: '$runtimeUser'"
}

Write-Host "PASS: container user = cvscanner"


# ================================================================
# 6. VERIFY PRODUCTION PROFILE
# ================================================================

Step "Checking production Spring profile"

$imageEnvironment =
    @(
        docker image inspect `
            $ImageName `
            --format '{{range .Config.Env}}{{println .}}{{end}}'
    )

if ($LASTEXITCODE -ne 0) {

    Fail "Unable to inspect Docker image environment."
}

if (
    $imageEnvironment -notcontains
    "SPRING_PROFILES_ACTIVE=prod"
) {

    Fail "Production Spring profile is not configured in image."
}

Write-Host "PASS: SPRING_PROFILES_ACTIVE=prod"


# ================================================================
# 7. COMPOSE VALIDATION
# ================================================================

Step "Validating production Compose configuration"

docker compose `
    --env-file $EnvFile `
    -f $ComposeFile `
    config `
    --quiet

if ($LASTEXITCODE -ne 0) {

    Fail "Production Compose validation failed."
}

Write-Host "PASS: Compose configuration valid"


# ================================================================
# 8. START PRODUCTION-LIKE STACK
# ================================================================

Step "Starting production-like environment"

$env:CVSCANNER_IMAGE_TAG = $ImageTag

docker compose `
    --env-file $EnvFile `
    -f $ComposeFile `
    up -d

if ($LASTEXITCODE -ne 0) {

    Fail "Production Compose startup failed."
}


# ================================================================
# 9. WAIT FOR APPLICATION HEALTH
# ================================================================

Step "Waiting for CVScanner health"

$maxAttempts = 30
$delaySeconds = 5
$healthy = $false

for (
    $attempt = 1;
    $attempt -le $maxAttempts;
    $attempt++
) {

    $containerId =
        docker compose `
            --env-file $EnvFile `
            -f $ComposeFile `
            ps `
            -q `
            cvscanner

    if (-not $containerId) {

        Write-Host "Attempt $attempt/$maxAttempts - container not ready"

        Start-Sleep -Seconds $delaySeconds

        continue
    }

    $healthStatus =
        docker inspect `
            --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' `
            $containerId

    Write-Host "Attempt $attempt/$maxAttempts - status=$healthStatus"

    if ($healthStatus -eq "healthy") {

        $healthy = $true

        break
    }

    if (
        $healthStatus -eq "exited" -or
        $healthStatus -eq "dead"
    ) {

        docker compose `
            --env-file $EnvFile `
            -f $ComposeFile `
            logs `
            --tail=100 `
            cvscanner

        Fail "CVScanner container terminated during startup."
    }

    Start-Sleep -Seconds $delaySeconds
}

if (-not $healthy) {

    docker compose `
        --env-file $EnvFile `
        -f $ComposeFile `
        logs `
        --tail=100 `
        cvscanner

    Fail "CVScanner did not become healthy in time."
}

Write-Host "PASS: CVScanner container healthy"


# ================================================================
# 10. HTTP SMOKE TEST
# ================================================================

Step "Running production HTTP smoke test"

powershell `
    -NoProfile `
    -ExecutionPolicy Bypass `
    -File .\scripts\production-smoke.ps1 `
    -Port $Port

if ($LASTEXITCODE -ne 0) {

    Fail "Production HTTP smoke test failed."
}


# ================================================================
# RESULT
# ================================================================

Write-Host ""
Write-Host "==============================================="
Write-Host " RELEASE GATE PASSED"
Write-Host "==============================================="
Write-Host ""
Write-Host "Image:      $ImageName"
Write-Host "HTTP port:  $Port"
Write-Host ""
Write-Host "Verified:"
Write-Host " - Maven tests"
Write-Host " - Integration tests"
Write-Host " - Docker build"
Write-Host " - Non-root runtime"
Write-Host " - Production profile"
Write-Host " - Compose configuration"
Write-Host " - Container health"
Write-Host " - Liveness"
Write-Host " - Readiness"
Write-Host ""