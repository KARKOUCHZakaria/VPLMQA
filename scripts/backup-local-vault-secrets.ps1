$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$backupFile = Join-Path $repo "secrets\vault-local-backup.json"
$vaultAddr = $env:VAULT_ADDR
if ([string]::IsNullOrWhiteSpace($vaultAddr)) { $vaultAddr = "http://localhost:8200" }
$vaultAddr = $vaultAddr.TrimEnd("/")
$vaultToken = $env:VAULT_TOKEN
if ([string]::IsNullOrWhiteSpace($vaultToken)) { $vaultToken = "dev-root-token" }
$headers = @{ "X-Vault-Token" = $vaultToken }

function Invoke-VaultJson {
    param([string]$Path)
    Invoke-RestMethod -Uri "$vaultAddr$Path" -Headers $headers -TimeoutSec 8
}

function Get-VaultKeys {
    param([string]$MetadataPath)
    try {
        $response = Invoke-VaultJson -Path "$MetadataPath`?list=true"
        return @($response.data.keys)
    } catch {
        return @()
    }
}

function Add-SecretBackup {
    param(
        [System.Collections.Generic.List[object]]$Items,
        [string]$KvPath
    )
    try {
        $response = Invoke-VaultJson -Path "/v1/secret/data/$KvPath"
        if ($null -ne $response.data -and $null -ne $response.data.data) {
            $Items.Add([ordered]@{
                path = $KvPath
                data = $response.data.data
            })
        }
    } catch {
        Write-Warning "Could not back up Vault secret '$KvPath': $($_.Exception.Message)"
    }
}

try {
    Invoke-VaultJson -Path "/v1/sys/health" | Out-Null
} catch {
    Write-Warning "Vault is not reachable at $vaultAddr. Skipping local Vault backup."
    exit 0
}

$items = [System.Collections.Generic.List[object]]::new()

foreach ($projectKey in Get-VaultKeys -MetadataPath "/v1/secret/metadata/vplmqa/projects") {
    $projectId = $projectKey.TrimEnd("/")
    if ([string]::IsNullOrWhiteSpace($projectId)) { continue }
    foreach ($aliasKey in Get-VaultKeys -MetadataPath "/v1/secret/metadata/vplmqa/projects/$projectId/e2e/") {
        $alias = $aliasKey.TrimEnd("/")
        if ([string]::IsNullOrWhiteSpace($alias)) { continue }
        Add-SecretBackup -Items $items -KvPath "vplmqa/projects/$projectId/e2e/$alias"
    }
}

foreach ($platformKey in Get-VaultKeys -MetadataPath "/v1/secret/metadata/vplmqa/platform") {
    $provider = $platformKey.TrimEnd("/")
    if ([string]::IsNullOrWhiteSpace($provider)) { continue }
    Add-SecretBackup -Items $items -KvPath "vplmqa/platform/$provider"
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $backupFile) | Out-Null
[ordered]@{
    savedAt = (Get-Date).ToString("o")
    vaultAddr = $vaultAddr
    secrets = @($items)
} | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 -Path $backupFile

Write-Host "Backed up $($items.Count) Vault secret(s) to $backupFile"
