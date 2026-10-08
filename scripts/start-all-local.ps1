$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $repo ".local-logs"
$startLog = Join-Path $logDir "start-all.log"

function Repair-ProcessPathEnvironment {
    $processPath = [Environment]::GetEnvironmentVariable("Path", "Process")
    if ([string]::IsNullOrWhiteSpace($processPath)) {
        $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
        $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
        $processPath = @($machinePath, $userPath) -join ";"
    }
    [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
    [Environment]::SetEnvironmentVariable("Path", $processPath, "Process")
}

Repair-ProcessPathEnvironment

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Write-Step {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -Encoding utf8 -Path $startLog -Value $line
}

function Invoke-LoggedScript {
    param(
        [string]$ScriptPath,
        [string]$Name
    )

    $previousPreference = $ErrorActionPreference
    try {
    $ErrorActionPreference = "Continue"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ScriptPath 2>&1 |
        ForEach-Object {
            $line = $_.ToString()
            Write-Host $line
            Add-Content -Encoding utf8 -Path $startLog -Value $line
        }
    $scriptExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($scriptExitCode -ne 0) {
        throw "$Name failed with exit code $scriptExitCode. See $startLog"
    }
}

function Test-HttpOk {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 300
    } catch {
        return $false
    }
}

function Wait-HttpReady {
    param([string]$Name, [string]$Url, [int]$TimeoutSeconds = 120)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while (-not (Test-HttpOk -Url $Url)) {
        if ((Get-Date) -ge $deadline) { throw "$Name did not become ready at $Url. See .local-logs." }
        Write-Step "Waiting for $Name at $Url..."
        Start-Sleep -Seconds 3
    }
    Write-Step "$Name is ready at $Url"
}

function Start-E2EChromeRunner {
    $healthUrl = "http://127.0.0.1:9223/health"
    if (Test-HttpOk -Url $healthUrl) {
        Write-Step "E2E Chrome runner is already available at $healthUrl"
        return
    }

    $runnerScript = Join-Path $PSScriptRoot "start-e2e-chrome-launcher.py"
    $runnerLog = Join-Path $logDir "e2e-chrome-runner.out.log"
    $runnerErr = Join-Path $logDir "e2e-chrome-runner.err.log"
    $python = Get-Command python -ErrorAction SilentlyContinue
    if (-not $python) {
        $fallback = Join-Path $PSScriptRoot "start-e2e-chrome-launcher.ps1"
        $command = @"
`$env:E2E_CHROME_LAUNCHER_BIND_ADDRESS = '0.0.0.0'
`$env:E2E_CHROME_DEBUG_ADDRESS = '0.0.0.0'
& '$fallback'
"@
        Start-Process -FilePath "powershell" `
            -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) `
            -WorkingDirectory $repo `
            -WindowStyle Hidden `
            -RedirectStandardOutput $runnerLog `
            -RedirectStandardError $runnerErr
    } else {
        $command = @"
`$env:E2E_CHROME_LAUNCHER_BIND_ADDRESS = '0.0.0.0'
`$env:E2E_CHROME_DEBUG_ADDRESS = '0.0.0.0'
& '$($python.Source)' '$runnerScript'
"@
        Start-Process -FilePath "powershell" `
            -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) `
            -WorkingDirectory $repo `
            -WindowStyle Hidden `
            -RedirectStandardOutput $runnerLog `
            -RedirectStandardError $runnerErr
    }

    $deadline = (Get-Date).AddSeconds(8)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpOk -Url $healthUrl) {
            Write-Step "E2E Chrome runner started at $healthUrl"
            return
        }
        Start-Sleep -Milliseconds 500
    }
    Write-Step "E2E Chrome runner did not answer yet. See $runnerErr"
}

Set-Location $repo
Set-Content -Encoding utf8 -Path $startLog -Value ""
try {
Write-Step "Starting VPLMQA local stack..."

Write-Step "Starting E2E Chrome runner..."
Start-E2EChromeRunner

Write-Step "Starting Docker infra, frontend, and agents..."
Invoke-LoggedScript -ScriptPath (Join-Path $PSScriptRoot "start-local-infra.ps1") -Name "start-local-infra"

Write-Step "Starting local Spring microservices..."
Invoke-LoggedScript -ScriptPath (Join-Path $PSScriptRoot "start-local-services.ps1") -Name "start-local-services"

Wait-HttpReady -Name "Gateway" -Url "http://localhost:8080/actuator/health"
Wait-HttpReady -Name "Frontend" -Url "http://localhost:3000"

Write-Step "VPLMQA local stack start command completed."
Write-Step "ALL SERVICES ARE READY - go to the app: http://localhost:3000"
Write-Step "Frontend: http://localhost:3000"
Write-Step "Gateway:  http://localhost:8080"
Write-Step "Eureka:   http://localhost:8761"
} catch {
    Write-Step "STARTUP FAILED: $($_.Exception.Message)"
    exit 1
}
