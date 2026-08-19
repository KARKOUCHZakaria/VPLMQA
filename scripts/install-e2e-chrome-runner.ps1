$ErrorActionPreference = "Stop"

$taskName = "VPLMQA-E2E-Chrome-Runner"
$launcher = Join-Path $PSScriptRoot "start-e2e-chrome-launcher.py"
$pythonCandidates = @(
    $env:E2E_CHROME_RUNNER_PYTHON,
    "C:\Users\zakar\AppData\Local\Python\bin\pythonw.exe",
    "C:\Users\zakar\AppData\Local\Python\bin\python.exe",
    "C:\Users\zakar\AppData\Local\Python\pythoncore-3.14-64\pythonw.exe",
    "C:\Users\zakar\AppData\Local\Python\pythoncore-3.14-64\python.exe",
    "C:\Users\zakar\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\pythonw.exe",
    "C:\Users\zakar\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe",
    "C:\Python312\pythonw.exe",
    "C:\Python311\pythonw.exe",
    "C:\Python310\pythonw.exe",
    "C:\Python312\python.exe",
    "C:\Python311\python.exe",
    "C:\Python310\python.exe"
) | Where-Object { $_ }

$python = $pythonCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $python) {
    $pathPython = Get-Command pythonw.exe -ErrorAction SilentlyContinue
    if (-not $pathPython) {
        $pathPython = Get-Command python.exe -ErrorAction SilentlyContinue
    }
    if ($pathPython) {
        $python = $pathPython.Source
    }
}

if (-not $python) {
    throw "No usable Python runtime was found for the hidden Chrome runner."
}

$action = New-ScheduledTaskAction -Execute $python -Argument "`"$launcher`""
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -MultipleInstances IgnoreNew

try {
    Register-ScheduledTask `
        -TaskName $taskName `
        -Action $action `
        -Trigger $trigger `
        -Settings $settings `
        -Description "Hidden local Chrome runner used by VPLMQA E2E Playwright pipelines." `
        -Force | Out-Null

    Start-ScheduledTask -TaskName $taskName
    Write-Host "Installed and started $taskName with Task Scheduler."
} catch {
    $startup = [Environment]::GetFolderPath("Startup")
    $shortcutPath = Join-Path $startup "$taskName.lnk"
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $python
    $shortcut.Arguments = "`"$launcher`""
    $shortcut.WorkingDirectory = (Split-Path $launcher -Parent)
    $shortcut.WindowStyle = 7
    $shortcut.Description = "Hidden local Chrome runner used by VPLMQA E2E Playwright pipelines."
    $shortcut.Save()

    Start-Process -FilePath $python -ArgumentList "`"$launcher`"" -WindowStyle Hidden
    Write-Host "Task Scheduler was denied, so installed a Startup shortcut instead."
    Write-Host "Started the runner hidden for this session."
}

Write-Host "Chrome will open only when the E2E pipeline calls the runner."
Write-Host "Health: http://127.0.0.1:9223/health"
