$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$secureKey = Read-Host "Gemini API key" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($plainKey)) {
        throw "Gemini API key cannot be empty."
    }
    $plainKey | docker exec -i vplmqa-vault-1 sh -c `
        'VAULT_TOKEN="$VAULT_DEV_ROOT_TOKEN_ID" vault kv put secret/vplmqa/platform/gemini api_key=-'
    if ($LASTEXITCODE -ne 0) {
        throw "Vault rejected the Gemini secret."
    }
} finally {
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    $plainKey = $null
}

Push-Location $repoRoot
try {
    docker compose up -d --no-build --force-recreate langgraph-agents
} finally {
    Pop-Location
}
Write-Host "Gemini key stored in Vault and LangGraph restarted."
