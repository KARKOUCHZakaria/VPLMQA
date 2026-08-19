$ErrorActionPreference = "Stop"

$taskName = "VPLMQA-E2E-Chrome-Runner"

try {
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
    Write-Host "Uninstalled $taskName."
} catch {
    Write-Host "$taskName is not installed."
}
