$ErrorActionPreference = "Stop"

$port = 9223
$chromeDebugPort = 9222
$profileDir = "C:\tmp\vplmqa-chrome-profile"
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
$chromePaths = @(
    "C:\Program Files\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
)
$script:chromeProcessId = $null

function Get-DockerHostAddress {
    if ($env:E2E_CHROME_HOST_ADDRESS) {
        return $env:E2E_CHROME_HOST_ADDRESS
    }

    if (Test-Path $envFile) {
        $cdpLine = Get-Content $envFile |
            Where-Object { $_ -match "^\s*E2E_BROWSER_CDP_URL\s*=" } |
            Select-Object -First 1
        if ($cdpLine -and $cdpLine -match "https?://([^:/]+)") {
            return $Matches[1]
        }
    }

    return "host.docker.internal"
}

$hostAddress = Get-DockerHostAddress
$launcherBindAddress = if ($env:E2E_CHROME_LAUNCHER_BIND_ADDRESS) { $env:E2E_CHROME_LAUNCHER_BIND_ADDRESS } else { "127.0.0.1" }
$chromeDebugAddress = if ($env:E2E_CHROME_DEBUG_ADDRESS) { $env:E2E_CHROME_DEBUG_ADDRESS } else { "0.0.0.0" }
$chromeDebugProbeAddress = if ($chromeDebugAddress -eq "0.0.0.0" -or $chromeDebugAddress -eq "::") { "127.0.0.1" } else { $chromeDebugAddress }
$dockerCdpAddress = if ($env:E2E_BROWSER_CDP_URL) { $env:E2E_BROWSER_CDP_URL } else { "http://$hostAddress`:$chromeDebugPort" }

function Get-ChromePath {
    $chrome = $chromePaths | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $chrome) {
        throw "Chrome was not found. Install Google Chrome or update this script with your chrome.exe path."
    }
    return $chrome
}

function Test-LocalPort {
    param(
        [string]$HostName,
        [int]$Port
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $result = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $result.AsyncWaitHandle.WaitOne(500)) {
            return $false
        }
        $client.EndConnect($result)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Start-E2EChrome {
    New-Item -ItemType Directory -Force -Path $profileDir | Out-Null

    if (-not (Test-LocalPort -HostName $chromeDebugProbeAddress -Port $chromeDebugPort)) {
        $chrome = Get-ChromePath
        $process = Start-Process $chrome -ArgumentList @(
            "--remote-debugging-port=$chromeDebugPort",
            "--remote-debugging-address=$chromeDebugAddress",
            "--remote-allow-origins=*",
            "--user-data-dir=$profileDir",
            "--ignore-certificate-errors",
            "--new-window",
            "about:blank"
        ) -WindowStyle Normal -PassThru
        $script:chromeProcessId = $process.Id
    }

    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $deadline) {
        if (Test-LocalPort -HostName $chromeDebugProbeAddress -Port $chromeDebugPort) {
            return
        }
        Start-Sleep -Milliseconds 300
    }

    throw "Chrome was launched, but debug port $chromeDebugPort is not reachable."
}

function Stop-E2EChrome {
    $processIds = @()
    if ($null -ne $script:chromeProcessId) {
        $processIds += $script:chromeProcessId
    }

    try {
        $portOwners = Get-NetTCPConnection -LocalPort $chromeDebugPort -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
        $processIds += $portOwners
    } catch {
    }

    $processIds |
        Where-Object { $_ } |
        Select-Object -Unique |
        ForEach-Object {
            try {
                taskkill.exe /PID $_ /T /F | Out-Null
            } catch {
                try {
                    Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
                } catch {
                }
            }
        }

    $script:chromeProcessId = $null
}

function Send-HttpResponse {
    param(
        [System.Net.Sockets.TcpClient]$Client,
        [int]$StatusCode,
        [string]$StatusText,
        [string]$Body
    )

    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $header = "HTTP/1.1 $StatusCode $StatusText`r`nContent-Type: application/json`r`nContent-Length: $($bodyBytes.Length)`r`nConnection: close`r`n`r`n"
    $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($header)
    $stream = $Client.GetStream()
    $stream.Write($headerBytes, 0, $headerBytes.Length)
    $stream.Write($bodyBytes, 0, $bodyBytes.Length)
    $stream.Flush()
}

$listenerAddress = [System.Net.IPAddress]::Parse($launcherBindAddress)
$listener = [System.Net.Sockets.TcpListener]::new($listenerAddress, $port)
$listener.Start()

Write-Host "VPLMQA E2E Chrome launcher is listening on $launcherBindAddress`:$port."
Write-Host "Chrome remote debugging will bind to $chromeDebugAddress`:$chromeDebugPort."
Write-Host "Keep this launcher running. Chrome will open only when you click Run in Chrome."

while ($true) {
    $client = $listener.AcceptTcpClient()
    try {
        $buffer = New-Object byte[] 4096
        $stream = $client.GetStream()
        $read = $stream.Read($buffer, 0, $buffer.Length)
        $request = [System.Text.Encoding]::ASCII.GetString($buffer, 0, $read)
        $requestLine = ($request -split "`r?`n")[0]

        if ($requestLine -match "^(GET|POST) /health ") {
            Send-HttpResponse -Client $client -StatusCode 200 -StatusText "OK" -Body '{"status":"ok"}'
        } elseif ($requestLine -match "^POST /launch ") {
            Start-E2EChrome
            Send-HttpResponse -Client $client -StatusCode 200 -StatusText "OK" -Body "{""status"":""launched"",""cdpUrl"":""$dockerCdpAddress""}"
        } elseif ($requestLine -match "^POST /close ") {
            Stop-E2EChrome
            Send-HttpResponse -Client $client -StatusCode 200 -StatusText "OK" -Body '{"status":"closed"}'
        } else {
            Send-HttpResponse -Client $client -StatusCode 404 -StatusText "Not Found" -Body '{"status":"not_found"}'
        }
    } catch {
        $message = ($_.Exception.Message -replace '"', '\"')
        Send-HttpResponse -Client $client -StatusCode 500 -StatusText "Internal Server Error" -Body "{""status"":""failed"",""message"":""$message""}"
    } finally {
        $client.Close()
    }
}


