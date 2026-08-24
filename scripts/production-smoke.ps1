param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:$Port"

Write-Host ""
Write-Host "==============================================="
Write-Host " CVScanner Production Smoke Verification"
Write-Host "==============================================="
Write-Host ""

function Test-Endpoint {

    param(
        [string]$Name,
        [string]$Url
    )

    Write-Host "Checking $Name ..."

    try {

        $response =
            Invoke-WebRequest `
                -Uri $Url `
                -UseBasicParsing `
                -TimeoutSec 10

        if ($response.StatusCode -ne 200) {

            throw "$Name returned HTTP $($response.StatusCode)"
        }

        Write-Host "$Name`: PASS (HTTP 200)"
    }
    catch {

        Write-Host "$Name`: FAIL"
        throw
    }
}


# ================================================================
# LIVENESS
# ================================================================

Test-Endpoint `
    -Name "Liveness" `
    -Url "$baseUrl/livez"


# ================================================================
# READINESS
# ================================================================

Test-Endpoint `
    -Name "Readiness" `
    -Url "$baseUrl/readyz"


# ================================================================
# RESULT
# ================================================================

Write-Host ""
Write-Host "==============================================="
Write-Host " PRODUCTION SMOKE VERIFICATION PASSED"
Write-Host "==============================================="
Write-Host ""