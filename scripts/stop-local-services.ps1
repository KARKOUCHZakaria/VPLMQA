$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $repo ".local-logs\spring-services.pids"

if (Test-Path $pidFile) {
    Get-Content $pidFile | ForEach-Object {
        $parts = $_.Split(",", 4)
        if ($parts.Length -lt 2) { return }
        $pidValue = [int]$parts[0]
        $name = $parts[1]
        $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
        if ($process) {
            Write-Host "Stopping $name (PID $pidValue)"
            Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
        }
    }
} else {
    Write-Host "No local Spring service PID file found. Checking VPLMQA service ports."
}

$ports = @(8761, 8087, 8080, 8081, 8088, 8089, 8082, 8084, 8085, 8086, 8083)
$portPids = @()
foreach ($port in $ports) {
    $lines = netstat -ano | Select-String -Pattern ":$port\s"
    foreach ($line in $lines) {
        if ($line -match "LISTENING\s+(\d+)") {
            $portPids += [int]$Matches[1]
        }
    }
}

$portPids | Sort-Object -Unique | ForEach-Object {
    $process = Get-Process -Id $_ -ErrorAction SilentlyContinue
    if ($process) {
        Write-Host "Stopping service process on VPLMQA port (PID $_)"
        Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
    }
}

if (Test-Path $pidFile) {
    Remove-Item -LiteralPath $pidFile -Force
}
