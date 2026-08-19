$ErrorActionPreference = "Stop"

$pythonLauncher = Join-Path $PSScriptRoot "start-e2e-chrome-launcher.py"
$powershellLauncher = Join-Path $PSScriptRoot "start-e2e-chrome-launcher.ps1"

Write-Host "Starting the VPLMQA E2E Chrome launcher."
Write-Host "Chrome will open only when you click Run in Chrome in the Pipeline tab."

$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
    & $python.Source $pythonLauncher
} else {
    Write-Host "Python was not found on PATH, falling back to the PowerShell launcher."
    & $powershellLauncher
}
