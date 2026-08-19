$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$secretDirectory = Join-Path $repoRoot "secrets"
$secretFile = Join-Path $secretDirectory "mistral_api_key.txt"

$secureKey = Read-Host "Mistral API key" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($plainKey)) {
        throw "Mistral API key cannot be empty."
    }
    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
    [System.IO.File]::WriteAllText($secretFile, $plainKey, [System.Text.UTF8Encoding]::new($false))
    attrib +H $secretFile
} finally {
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    $plainKey = $null
}

Push-Location $repoRoot
try {
    docker compose up -d --force-recreate vault-init langgraph-agents
} finally {
    Pop-Location
}
Write-Host "Mistral key stored as a local Docker secret, loaded into Vault, and LangGraph restarted."
