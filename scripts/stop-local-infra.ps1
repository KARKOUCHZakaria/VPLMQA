$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
$dockerConfig = Join-Path $repo ".docker-cli-config"
New-Item -ItemType Directory -Force -Path $dockerConfig | Out-Null
$env:DOCKER_CONFIG = $dockerConfig
$env:COMPOSE_PROGRESS = "plain"
$logDir = Join-Path $repo ".local-logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$dockerOut = Join-Path $logDir "docker-stop.out.log"
$dockerErr = Join-Path $logDir "docker-stop.err.log"

$vaultBackupScript = Join-Path $PSScriptRoot "backup-local-vault-secrets.ps1"
if (Test-Path $vaultBackupScript) {
    Write-Host "Backing up local Vault secrets before Docker shutdown..."
    & powershell -NoProfile -ExecutionPolicy Bypass -File $vaultBackupScript
}

function Invoke-DockerComposeStop {
    param([string[]]$Services, [string]$Name)

    $out = Join-Path $logDir "$Name.out.log"
    $err = Join-Path $logDir "$Name.err.log"
    $args = @("compose", "--env-file", ".env", "-f", "docker-compose.local.yml", "stop") + $Services

    $proc = Start-Process -FilePath "docker" `
        -ArgumentList $args `
        -WorkingDirectory $repo `
        -NoNewWindow `
        -Wait `
        -PassThru `
        -RedirectStandardOutput $out `
        -RedirectStandardError $err

    Get-Content -LiteralPath $out -ErrorAction SilentlyContinue | Write-Host
    Get-Content -LiteralPath $err -ErrorAction SilentlyContinue | Write-Host

    if ($proc.ExitCode -ne 0) {
        throw "$Name failed with exit code $($proc.ExitCode). See $err"
    }
}

Write-Host "Stopping LangGraph and Kafka first..."
Invoke-DockerComposeStop -Name "docker-stop-agents-kafka" -Services @("langgraph-agents", "kafka")
Start-Sleep -Seconds 12

$process = Start-Process -FilePath "docker" `
    -ArgumentList @("compose", "--env-file", ".env", "-f", "docker-compose.local.yml", "stop") `
    -WorkingDirectory $repo `
    -NoNewWindow `
    -Wait `
    -PassThru `
    -RedirectStandardOutput $dockerOut `
    -RedirectStandardError $dockerErr

Get-Content -LiteralPath $dockerOut -ErrorAction SilentlyContinue | Write-Host
Get-Content -LiteralPath $dockerErr -ErrorAction SilentlyContinue | Write-Host

if ($process.ExitCode -ne 0) {
    throw "Docker compose failed with exit code $($process.ExitCode). See $dockerErr"
}
