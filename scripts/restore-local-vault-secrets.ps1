$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$backupFile = Join-Path $repo "secrets\vault-local-backup.json"
$vaultAddr = $env:VAULT_ADDR
if ([string]::IsNullOrWhiteSpace($vaultAddr)) { $vaultAddr = "http://localhost:8200" }
$vaultAddr = $vaultAddr.TrimEnd("/")
$vaultToken = $env:VAULT_TOKEN
if ([string]::IsNullOrWhiteSpace($vaultToken)) { $vaultToken = "dev-root-token" }
$headers = @{ "X-Vault-Token" = $vaultToken }

if (-not (Test-Path $backupFile)) {
    Write-Host "No local Vault backup found at $backupFile"
    exit 0
}

try {
    Invoke-RestMethod -Uri "$vaultAddr/v1/sys/health" -Headers $headers -TimeoutSec 8 | Out-Null
} catch {
    Write-Warning "Vault is not reachable at $vaultAddr. Skipping local Vault restore."
    exit 0
}

$backup = Get-Content -Raw -Path $backupFile | ConvertFrom-Json
$secrets = @($backup.secrets)
$restored = 0

foreach ($secret in $secrets) {
    if ([string]::IsNullOrWhiteSpace($secret.path)) { continue }
    $body = @{ data = $secret.data } | ConvertTo-Json -Depth 20
    try {
        Invoke-RestMethod `
            -Uri "$vaultAddr/v1/secret/data/$($secret.path)" `
            -Method Post `
            -Headers $headers `
            -ContentType "application/json" `
            -Body $body `
            -TimeoutSec 8 | Out-Null
        $restored += 1
    } catch {
        Write-Warning "Could not restore Vault secret '$($secret.path)': $($_.Exception.Message)"
    }
}

Write-Host "Restored $restored Vault secret(s) from $backupFile"
