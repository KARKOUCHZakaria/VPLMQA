$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$mvn = Join-Path $repo "maven\apache-maven-3.9.9\bin\mvn.cmd"
$vaultTokenFile = Join-Path $repo "secrets\vault_token.txt"
$envFile = Join-Path $repo ".env"
$logDir = Join-Path $repo ".local-logs"
$pidFile = Join-Path $logDir "spring-services.pids"

function Repair-ProcessPathEnvironment {
    $processPath = [Environment]::GetEnvironmentVariable("Path", "Process")
    if ([string]::IsNullOrWhiteSpace($processPath)) {
        $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
        $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
        $processPath = @($machinePath, $userPath) -join ";"
    }
    [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
    [Environment]::SetEnvironmentVariable("Path", $processPath, "Process")
}

Repair-ProcessPathEnvironment

function Test-LocalPort {
    param([int]$Port)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $connected = $async.AsyncWaitHandle.WaitOne(1500, $false)
        if ($connected) { $client.EndConnect($async) }
        $client.Close()
        return $connected
    } catch {
        return $false
    }
}

function Wait-LocalPorts {
    param(
        [array]$Ports,
        [int]$TimeoutSeconds = 180
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $missing = @($Ports | Where-Object { -not (Test-LocalPort -Port $_.Port) })
        if ($missing.Count -eq 0) { return }
        Start-Sleep -Seconds 3
    }
    $stillMissing = @($Ports | Where-Object { -not (Test-LocalPort -Port $_.Port) } | ForEach-Object { "$($_.Name) on $($_.Port)" })
    throw "Required local infra is not reachable: $($stillMissing -join ', ')"
}

if (-not (Test-Path $mvn)) {
    throw "Maven was not found at $mvn"
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $vaultTokenFile) | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$stopScript = Join-Path $PSScriptRoot "stop-local-services.ps1"
if (Test-Path $stopScript) {
    Write-Host "Clearing any existing local Spring services before start..."
    & powershell -NoProfile -ExecutionPolicy Bypass -File $stopScript
}

if (Test-Path $pidFile) { Remove-Item -LiteralPath $pidFile -Force }

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#") -or -not $line.Contains("=")) { return }
        $parts = $line.Split("=", 2)
        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        $commentIndex = $value.IndexOf(" #")
        if ($commentIndex -ge 0) { $value = $value.Substring(0, $commentIndex).Trim() }
        if (-not [string]::IsNullOrWhiteSpace($key) -and [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key, "Process"))) {
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
        }
    }
}

if (-not (Test-Path $vaultTokenFile)) {
    $token = $env:VAULT_TOKEN
    if ([string]::IsNullOrWhiteSpace($token)) { $token = "dev-root-token" }
    $token | Set-Content -NoNewline -Encoding ascii $vaultTokenFile
}

$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_CONFIG_IMPORT = "optional:configserver:http://localhost:8087"
$env:SPRING_CLOUD_CONFIG_URI = "http://localhost:8087"
$env:EUREKA_URI = "http://localhost:8761/eureka"
$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE = "http://localhost:8761/eureka"
$env:EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE = "http://localhost:8761/eureka"
$env:EUREKA_INSTANCE_HOSTNAME = "localhost"
$env:EUREKA_INSTANCE_PREFER_IP_ADDRESS = "false"

$env:DB_HOST = "localhost"
$env:DB_PORT = "5434"
$env:DB_USER = $env:POSTGRES_USER
if ([string]::IsNullOrWhiteSpace($env:DB_USER)) { $env:DB_USER = "vplmqa" }
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { $env:DB_PASSWORD = "change_me_in_prod" }
$env:POSTGRES_USER = $env:DB_USER
$env:POSTGRES_PASSWORD = $env:DB_PASSWORD

$env:REDIS_HOST = "localhost"
if ([string]::IsNullOrWhiteSpace($env:REDIS_PASSWORD)) { $env:REDIS_PASSWORD = "change_me_in_prod" }
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = $env:KAFKA_BOOTSTRAP_SERVERS

$env:MINIO_URL = "http://localhost:9005"
$env:MINIO_ENDPOINT = "http://localhost:9005"
if ([string]::IsNullOrWhiteSpace($env:MINIO_ROOT_USER)) { $env:MINIO_ROOT_USER = "vplmqa" }
if ([string]::IsNullOrWhiteSpace($env:MINIO_ROOT_PASSWORD)) { $env:MINIO_ROOT_PASSWORD = "change_me_in_prod" }

$env:VAULT_ADDR = "http://localhost:8200"
$env:VAULT_URL = "http://localhost:8200"
$env:VAULT_TOKEN_FILE = $vaultTokenFile
$env:LANGGRAPH_AGENTS_URL = "http://localhost:8090"
$env:PART1_AGENT_URL = "http://localhost:8090"
$env:PROJECT_SERVICE_URL = "http://localhost:8088"
$env:DESIGN_SERVICE_URL = "http://localhost:8082"
$env:MANAGEMENT_TRACING_ENABLED = "false"
$env:JAVA_TOOL_OPTIONS = "--add-opens=java.base/java.lang=ALL-UNNAMED"

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    $env:JWT_SECRET = "your-256-bit-secret-here-minimum-64-characters-required-for-HS512"
}
$env:SECURITY_JWT_SECRET = $env:JWT_SECRET
$env:SECURITY_JWT_ISSUER = "vplmqa-local"
$env:SECURITY_JWT_ACCESS_TOKEN_TTL_SECONDS = "3600"
$env:SECURITY_JWT_REFRESH_TOKEN_TTL_SECONDS = "604800"

$env:SPRING_MAIL_HOST = "localhost"
$env:SPRING_MAIL_PORT = "1025"
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH = "false"
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = "false"
$env:SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS = "file:$repo/config-service/config-repo"

$requiredInfra = @(
    @{ Name = "PostgreSQL"; Port = 5434 },
    @{ Name = "Redis"; Port = 6379 },
    @{ Name = "Kafka"; Port = 9092 },
    @{ Name = "MinIO"; Port = 9005 },
    @{ Name = "Vault"; Port = 8200 }
)
$missingInfra = @($requiredInfra | Where-Object { -not (Test-LocalPort -Port $_.Port) })
if ($missingInfra.Count -gt 0) {
    Write-Host "Required local infra is not ready. Starting Docker infra first..."
    $infraScript = Join-Path $PSScriptRoot "start-local-infra.ps1"
    & powershell -NoProfile -ExecutionPolicy Bypass -File $infraScript
    Wait-LocalPorts -Ports $requiredInfra -TimeoutSeconds 240
} else {
    Write-Host "Local Docker infra ports are reachable."
}

$services = @(
    @{ Name = "eureka-server"; Port = 8761; Db = "postgres" },
    @{ Name = "config-service"; Port = 8087; Db = "postgres" },
    @{ Name = "auth-service"; Port = 8081; Db = "auth_db" },
    @{ Name = "project-service"; Port = 8088; Db = "project_db" },
    @{ Name = "member-service"; Port = 8089; Db = "member_db" },
    @{ Name = "design-service"; Port = 8082; Db = "design_db" },
    @{ Name = "ticket-service"; Port = 8084; Db = "ticket_db" },
    @{ Name = "notification-service"; Port = 8085; Db = "notification_db" },
    @{ Name = "analytics-service"; Port = 8086; Db = "analytics_db" },
    @{ Name = "e2e-service"; Port = 8083; Db = "tests_db" },
    @{ Name = "gateway"; Port = 8080; Db = "postgres" }
)

Write-Host "Starting Spring services locally in hidden Maven processes."
Write-Host "Start infra first with: .\scripts\start-local-infra.ps1"
Write-Host "Installing shared module vplmqa-common..."
& $mvn -pl vplmqa-common install -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install vplmqa-common"
}

foreach ($service in $services) {
    $name = $service.Name
    $port = $service.Port
    $log = Join-Path $logDir "$name.log"
    $profile = if ($name -eq "config-service") { "native" } else { "dev" }
    $configImport = if ($name -eq "config-service") { "" } else { $env:SPRING_CONFIG_IMPORT }
    $configClientEnabled = if ($name -eq "config-service") { "false" } else { "true" }
    $datasourceUrl = "jdbc:postgresql://$($env:DB_HOST):$($env:DB_PORT)/$($service.Db)"
    $cmd = @"
Set-Location '$repo'
`$env:SERVER_PORT='$port'
`$env:SPRING_PROFILES_ACTIVE='$profile'
`$env:SPRING_CONFIG_IMPORT='$configImport'
`$env:SPRING_CLOUD_CONFIG_ENABLED='$configClientEnabled'
`$env:SPRING_CLOUD_CONFIG_URI='$env:SPRING_CLOUD_CONFIG_URI'
`$env:SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS='$env:SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS'
`$env:EUREKA_URI='$env:EUREKA_URI'
`$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE='$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE'
`$env:EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE='$env:EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE'
`$env:EUREKA_INSTANCE_HOSTNAME='$env:EUREKA_INSTANCE_HOSTNAME'
`$env:EUREKA_INSTANCE_PREFER_IP_ADDRESS='$env:EUREKA_INSTANCE_PREFER_IP_ADDRESS'
`$env:DB_HOST='$env:DB_HOST'
`$env:DB_PORT='$env:DB_PORT'
`$env:DB_USER='$env:DB_USER'
`$env:DB_PASSWORD='$env:DB_PASSWORD'
`$env:POSTGRES_USER='$env:POSTGRES_USER'
`$env:POSTGRES_PASSWORD='$env:POSTGRES_PASSWORD'
`$env:SPRING_DATASOURCE_URL='$datasourceUrl'
`$env:SPRING_DATASOURCE_USERNAME='$env:DB_USER'
`$env:SPRING_DATASOURCE_PASSWORD='$env:DB_PASSWORD'
`$env:REDIS_HOST='$env:REDIS_HOST'
`$env:REDIS_PASSWORD='$env:REDIS_PASSWORD'
`$env:KAFKA_BOOTSTRAP_SERVERS='$env:KAFKA_BOOTSTRAP_SERVERS'
`$env:SPRING_KAFKA_BOOTSTRAP_SERVERS='$env:SPRING_KAFKA_BOOTSTRAP_SERVERS'
`$env:MINIO_URL='$env:MINIO_URL'
`$env:MINIO_ENDPOINT='$env:MINIO_ENDPOINT'
`$env:MINIO_ROOT_USER='$env:MINIO_ROOT_USER'
`$env:MINIO_ROOT_PASSWORD='$env:MINIO_ROOT_PASSWORD'
`$env:VAULT_ADDR='$env:VAULT_ADDR'
`$env:VAULT_URL='$env:VAULT_URL'
`$env:VAULT_TOKEN_FILE='$env:VAULT_TOKEN_FILE'
`$env:LANGGRAPH_AGENTS_URL='$env:LANGGRAPH_AGENTS_URL'
`$env:PART1_AGENT_URL='$env:PART1_AGENT_URL'
`$env:PROJECT_SERVICE_URL='$env:PROJECT_SERVICE_URL'
`$env:DESIGN_SERVICE_URL='$env:DESIGN_SERVICE_URL'
`$env:MANAGEMENT_TRACING_ENABLED='$env:MANAGEMENT_TRACING_ENABLED'
`$env:JWT_SECRET='$env:JWT_SECRET'
`$env:SECURITY_JWT_SECRET='$env:SECURITY_JWT_SECRET'
`$env:SECURITY_JWT_ISSUER='$env:SECURITY_JWT_ISSUER'
`$env:SECURITY_JWT_ACCESS_TOKEN_TTL_SECONDS='$env:SECURITY_JWT_ACCESS_TOKEN_TTL_SECONDS'
`$env:SECURITY_JWT_REFRESH_TOKEN_TTL_SECONDS='$env:SECURITY_JWT_REFRESH_TOKEN_TTL_SECONDS'
`$env:SPRING_MAIL_HOST='$env:SPRING_MAIL_HOST'
`$env:SPRING_MAIL_PORT='$env:SPRING_MAIL_PORT'
`$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH='$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH'
`$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE='$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE'
`$env:JAVA_TOOL_OPTIONS='$env:JAVA_TOOL_OPTIONS'
& '$mvn' -pl $name spring-boot:run *> '$log'
"@
    $process = Start-Process powershell.exe -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $cmd -WindowStyle Hidden -PassThru
    "$($process.Id),$name,$port,$log" | Add-Content -Encoding ascii $pidFile
    Write-Host "Started $name on $port (PID $($process.Id), log $log)"
    Start-Sleep -Seconds 8
}

Write-Host ""
Write-Host "Waiting for Spring service health checks..."
$deadline = (Get-Date).AddMinutes(15)
$remaining = @{}
foreach ($service in $services) { $remaining[$service.Name] = $service }

while ($remaining.Count -gt 0 -and (Get-Date) -lt $deadline) {
    foreach ($name in @($remaining.Keys)) {
        $service = $remaining[$name]
        $healthUrl = "http://localhost:$($service.Port)/actuator/health"
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec 4
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Host "Healthy: $name on $($service.Port)"
                $remaining.Remove($name)
            }
        } catch {
            Start-Sleep -Milliseconds 200
        }
    }
    if ($remaining.Count -gt 0) {
        Start-Sleep -Seconds 5
    }
}

if ($remaining.Count -gt 0) {
    $failedNames = ($remaining.Keys | Sort-Object) -join ", "
    Write-Host "Services not healthy before timeout: $failedNames"
    foreach ($name in ($remaining.Keys | Sort-Object)) {
        $log = Join-Path $logDir "$name.log"
        Write-Host ""
        Write-Host "Last log lines for $name ($log):"
        Get-Content -LiteralPath $log -Tail 40 -ErrorAction SilentlyContinue
    }
    throw "Not all Spring services became healthy: $failedNames"
}

Write-Host ""
Write-Host "ALL SPRING SERVICES ARE READY - go to the app: http://localhost:3000"
Write-Host "  Frontend: http://localhost:3000"
Write-Host "  Gateway:  http://localhost:8080"
Write-Host "  Eureka:   http://localhost:8761"
Write-Host "Logs are in: $logDir"
