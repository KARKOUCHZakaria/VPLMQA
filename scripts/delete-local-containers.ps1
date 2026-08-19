$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
$dockerConfig = Join-Path $repo ".docker-cli-config"
New-Item -ItemType Directory -Force -Path $dockerConfig | Out-Null
$env:DOCKER_CONFIG = $dockerConfig
$env:COMPOSE_PROGRESS = "plain"
$logDir = Join-Path $repo ".local-logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$vaultBackupScript = Join-Path $PSScriptRoot "backup-local-vault-secrets.ps1"
if (Test-Path $vaultBackupScript) {
    Write-Host "Backing up local Vault secrets before deleting containers..."
    & powershell -NoProfile -ExecutionPolicy Bypass -File $vaultBackupScript
}

$dockerOut = Join-Path $logDir "docker-delete-containers.out.log"
$dockerErr = Join-Path $logDir "docker-delete-containers.err.log"
$process = Start-Process -FilePath "docker" `
    -ArgumentList @("compose", "--env-file", ".env", "-f", "docker-compose.local.yml", "down") `
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

Write-Host "Local Docker containers deleted. Volumes were not deleted."
