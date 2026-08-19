param(
    [switch]$PruneVolumes,
    [switch]$CompactVhd,
    [int]$KeepBuildCacheGB = 10,
    [int]$UnusedImageAgeDays = 14
)

$ErrorActionPreference = "Stop"

if ($KeepBuildCacheGB -lt 1) {
    throw "KeepBuildCacheGB must be at least 1."
}

Write-Host "Removing stopped containers and unused networks..."
docker container prune -f
docker network prune -f

Write-Host "Keeping at most ${KeepBuildCacheGB}GB of build cache..."
docker builder prune -f --keep-storage "${KeepBuildCacheGB}GB"

Write-Host "Removing unused images older than $UnusedImageAgeDays days..."
docker image prune -af --filter "until=${UnusedImageAgeDays}d"

if ($PruneVolumes) {
    Write-Warning "Pruning unused Docker volumes. This can delete database/artifact data if containers are down."
    docker volume prune -f
}

if ($CompactVhd) {
    $vhdPath = Join-Path $env:LOCALAPPDATA "Docker\wsl\disk\docker_data.vhdx"
    if (-not (Test-Path $vhdPath)) {
        throw "Docker VHDX not found at $vhdPath"
    }

    Write-Host "Stopping Docker Desktop and WSL before VHDX compaction..."
    Stop-Process -Name "Docker Desktop","com.docker.backend","com.docker.build","docker","docker-agent","docker-sandbox" -Force -ErrorAction SilentlyContinue
    wsl --shutdown
    Start-Sleep -Seconds 8

    Write-Host "Compacting $vhdPath ..."
    Optimize-VHD -Path $vhdPath -Mode Full

    Write-Host "Restarting Docker Desktop..."
    Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe" -WindowStyle Hidden
}

Write-Host "Docker cleanup complete."
