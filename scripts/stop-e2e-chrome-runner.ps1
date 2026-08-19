$ErrorActionPreference = "Stop"

$taskName = "VPLMQA-E2E-Chrome-Runner"

try {
    Stop-ScheduledTask -TaskName $taskName -ErrorAction Stop
    Write-Host "Stopped $taskName."
} catch {
    Write-Host "$taskName was not running or is not installed."
}
