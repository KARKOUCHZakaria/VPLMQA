$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
$dockerConfig = Join-Path $repo ".docker-cli-config"
New-Item -ItemType Directory -Force -Path $dockerConfig | Out-Null
$env:DOCKER_CONFIG = $dockerConfig
$env:COMPOSE_PROGRESS = "plain"
$logDir = Join-Path $repo ".local-logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if (-not (Test-Path ".env")) {
    throw "Missing .env at $repo"
}

Write-Host "Starting local infra and frontend dev container..."
$dockerOut = Join-Path $logDir "docker-start.out.log"
Set-Content -LiteralPath $dockerOut -Encoding utf8 -Value ""
$previousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = "Continue"
    & docker compose --env-file .env -f docker-compose.local.yml up -d --quiet-pull --build postgres redis zookeeper kafka minio vault vault-init mailhog front-end 2>&1 |
        ForEach-Object {
            Write-Host $_.ToString()
            Add-Content -LiteralPath $dockerOut -Encoding utf8 -Value $_.ToString()
        }
    $dockerExitCode = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousPreference }
if ($dockerExitCode -ne 0) {
    throw "Docker compose failed with exit code $dockerExitCode. See $dockerOut"
}

function Invoke-DockerCompose {
    param([string[]]$Arguments, [string]$Name)
    $out = Join-Path $logDir "$Name.out.log"
    Set-Content -LiteralPath $out -Encoding utf8 -Value ""
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & docker @Arguments 2>&1 | ForEach-Object {
            Write-Host $_.ToString()
            Add-Content -LiteralPath $out -Encoding utf8 -Value $_.ToString()
        }
        $dockerExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($dockerExitCode -ne 0) {
        throw "$Name failed with exit code $dockerExitCode. See $out"
    }
}

function Get-ContainerState {
    param([string]$Name, [string]$Template)
    $value = docker inspect -f $Template $Name 2>$null
    if ($LASTEXITCODE -ne 0) {
        return ""
    }
    return $value
}

function Wait-ContainerHealthy {
    param([string]$Name, [int]$TimeoutSeconds = 180)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $status = Get-ContainerState -Name $Name -Template "{{.State.Status}}"
        $health = Get-ContainerState -Name $Name -Template "{{if .State.Health}}{{.State.Health.Status}}{{end}}"

        if ($status -eq "running" -and ($health -eq "healthy" -or [string]::IsNullOrWhiteSpace($health))) {
            return $true
        }

        if ($status -eq "exited" -or $status -eq "dead") {
            return $false
        }

        Write-Host "Waiting for $Name to be healthy... status=$status health=$health"
        Start-Sleep -Seconds 5
    }

    return $false
}

if (-not (Wait-ContainerHealthy -Name "vplmqa-local-kafka-1" -TimeoutSeconds 180)) {
    Write-Host "Kafka is not healthy. Restarting ZooKeeper and Kafka to clear stale broker sessions..."
    Invoke-DockerCompose `
        -Name "docker-restart-kafka" `
        -Arguments @("compose", "--env-file", ".env", "-f", "docker-compose.local.yml", "stop", "kafka", "zookeeper")
    Start-Sleep -Seconds 12
    Invoke-DockerCompose `
        -Name "docker-up-kafka" `
        -Arguments @("compose", "--env-file", ".env", "-f", "docker-compose.local.yml", "up", "-d", "zookeeper", "kafka")

    if (-not (Wait-ContainerHealthy -Name "vplmqa-local-kafka-1" -TimeoutSeconds 180)) {
        Write-Host "Kafka logs:"
        docker logs --tail 80 vplmqa-local-kafka-1 2>$null | Write-Host
        throw "Kafka failed to become healthy after restart."
    }
}

Write-Host "Starting LangGraph agents after Kafka is healthy..."
Invoke-DockerCompose `
    -Name "docker-up-langgraph" `
    -Arguments @("compose", "--env-file", ".env", "-f", "docker-compose.local.yml", "up", "-d", "--build", "langgraph-agents")

$vaultRestoreScript = Join-Path $PSScriptRoot "restore-local-vault-secrets.ps1"
if (Test-Path $vaultRestoreScript) {
    Write-Host "Restoring local Vault secrets after Docker startup..."
    & powershell -NoProfile -ExecutionPolicy Bypass -File $vaultRestoreScript
}

Write-Host ""
Write-Host "Local infra started:"
Write-Host "  PostgreSQL: localhost:5434"
Write-Host "  Redis:      localhost:6379"
Write-Host "  Kafka:      localhost:9092"
Write-Host "  MinIO:      http://localhost:9006"
Write-Host "  Vault:      http://localhost:8200"
Write-Host "  Mailhog:    http://localhost:8025"
Write-Host "  Frontend:   http://localhost:3000"
Write-Host "  Agents:     http://localhost:8090"
