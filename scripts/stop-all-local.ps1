$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $repo ".local-logs"
$stopLog = Join-Path $logDir "stop-all.log"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Write-Step {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -Encoding utf8 -Path $stopLog -Value $line
}

function Invoke-LoggedScript {
    param(
        [string]$ScriptPath,
        [string]$Name
    )

    $stdout = Join-Path $logDir "$Name.out.log"
    $stderr = Join-Path $logDir "$Name.err.log"
    $process = Start-Process -FilePath "powershell" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $ScriptPath) `
        -WorkingDirectory $repo `
        -NoNewWindow `
        -Wait `
        -PassThru `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr

    Get-Content -LiteralPath $stdout -ErrorAction SilentlyContinue | Tee-Object -FilePath $stopLog -Append
    Get-Content -LiteralPath $stderr -ErrorAction SilentlyContinue | Tee-Object -FilePath $stopLog -Append

    if ($process.ExitCode -ne 0) {
        throw "$Name failed with exit code $($process.ExitCode). See $stderr"
    }
}

Set-Location $repo
Write-Step "Stopping VPLMQA local stack..."

Write-Step "Stopping local Spring microservices..."
Invoke-LoggedScript -ScriptPath (Join-Path $PSScriptRoot "stop-local-services.ps1") -Name "stop-local-services"

$ports = @(8761,8087,8080,8081,8088,8089,8082,8084,8085,8086,8083)
$pids = @()
foreach ($port in $ports) {
    $lines = netstat -ano | Select-String -Pattern ":$port\s"
    foreach ($line in $lines) {
        if ($line -match "LISTENING\s+(\d+)") {
            $pids += [int]$Matches[1]
        }
    }
}

$pids | Sort-Object -Unique | ForEach-Object {
    Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
    Write-Step "Stopped local service process PID $_"
}

Write-Step "Stopping Docker infra/frontend/agents..."
Invoke-LoggedScript -ScriptPath (Join-Path $PSScriptRoot "stop-local-infra.ps1") -Name "stop-local-infra"

Write-Step "VPLMQA local stack stopped."
