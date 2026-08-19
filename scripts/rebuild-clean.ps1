param(
    [string[]]$Services = @(),
    [switch]$Down,
    [switch]$PruneVolumes,
    [switch]$CompactVhd
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

Set-Location $repoRoot

if ($Down) {
    docker compose down --remove-orphans
}

& "$PSScriptRoot\docker-clean.ps1" -PruneVolumes:$PruneVolumes -CompactVhd:$CompactVhd

if ($Services.Count -gt 0) {
    docker compose build @Services
    docker compose up -d @Services
} else {
    docker compose build
    docker compose up -d
}
