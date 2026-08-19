param(
    [string]$TaskName = "VPLMQA Docker Maintenance"
)

$ErrorActionPreference = "Stop"
$cleanupScript = Join-Path $PSScriptRoot "docker-clean.ps1"

if (-not (Test-Path -LiteralPath $cleanupScript)) {
    throw "Cleanup script not found at $cleanupScript"
}

$action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$cleanupScript`" -KeepBuildCacheGB 10 -UnusedImageAgeDays 14"
$trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At 3am
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1)

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Bounds Docker build cache and removes stale images without deleting project volumes." `
    -Force | Out-Null

Write-Host "Installed scheduled task '$TaskName'."
