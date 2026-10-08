Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $repo ".local-logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$form = New-Object System.Windows.Forms.Form
$form.Text = "VPLMQA Local Launcher"
$form.Size = New-Object System.Drawing.Size(760, 560)
$form.StartPosition = "CenterScreen"
$form.BackColor = [System.Drawing.Color]::FromArgb(250, 247, 255)

$title = New-Object System.Windows.Forms.Label
$title.Text = "VPLMQA Local Stack"
$title.Font = New-Object System.Drawing.Font("Segoe UI", 18, [System.Drawing.FontStyle]::Bold)
$title.ForeColor = [System.Drawing.Color]::FromArgb(38, 14, 64)
$title.AutoSize = $true
$title.Location = New-Object System.Drawing.Point(28, 24)
$form.Controls.Add($title)

$status = New-Object System.Windows.Forms.Label
$status.Text = "Ready."
$status.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$status.ForeColor = [System.Drawing.Color]::FromArgb(91, 56, 134)
$status.AutoSize = $false
$status.Size = New-Object System.Drawing.Size(490, 46)
$status.Location = New-Object System.Drawing.Point(32, 68)
$form.Controls.Add($status)

$startButton = New-Object System.Windows.Forms.Button
$startButton.Text = "Start All"
$startButton.Font = New-Object System.Drawing.Font("Segoe UI", 12, [System.Drawing.FontStyle]::Bold)
$startButton.Size = New-Object System.Drawing.Size(220, 62)
$startButton.Location = New-Object System.Drawing.Point(32, 130)
$startButton.BackColor = [System.Drawing.Color]::FromArgb(124, 58, 237)
$startButton.ForeColor = [System.Drawing.Color]::White
$startButton.FlatStyle = "Flat"
$form.Controls.Add($startButton)

$stopButton = New-Object System.Windows.Forms.Button
$stopButton.Text = "Stop All"
$stopButton.Font = New-Object System.Drawing.Font("Segoe UI", 12, [System.Drawing.FontStyle]::Bold)
$stopButton.Size = New-Object System.Drawing.Size(220, 62)
$stopButton.Location = New-Object System.Drawing.Point(292, 130)
$stopButton.BackColor = [System.Drawing.Color]::FromArgb(239, 68, 68)
$stopButton.ForeColor = [System.Drawing.Color]::White
$stopButton.FlatStyle = "Flat"
$form.Controls.Add($stopButton)

$openButton = New-Object System.Windows.Forms.Button
$openButton.Text = "Open App"
$openButton.Font = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
$openButton.Size = New-Object System.Drawing.Size(150, 38)
$openButton.Location = New-Object System.Drawing.Point(32, 218)
$openButton.BackColor = [System.Drawing.Color]::White
$openButton.ForeColor = [System.Drawing.Color]::FromArgb(124, 58, 237)
$openButton.FlatStyle = "Flat"
$form.Controls.Add($openButton)

$logsButton = New-Object System.Windows.Forms.Button
$logsButton.Text = "Open Logs"
$logsButton.Font = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
$logsButton.Size = New-Object System.Drawing.Size(150, 38)
$logsButton.Location = New-Object System.Drawing.Point(198, 218)
$logsButton.BackColor = [System.Drawing.Color]::White
$logsButton.ForeColor = [System.Drawing.Color]::FromArgb(124, 58, 237)
$logsButton.FlatStyle = "Flat"
$form.Controls.Add($logsButton)

$deleteButton = New-Object System.Windows.Forms.Button
$deleteButton.Text = "Delete Containers"
$deleteButton.Font = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
$deleteButton.Size = New-Object System.Drawing.Size(150, 38)
$deleteButton.Location = New-Object System.Drawing.Point(364, 218)
$deleteButton.BackColor = [System.Drawing.Color]::White
$deleteButton.ForeColor = [System.Drawing.Color]::FromArgb(220, 38, 38)
$deleteButton.FlatStyle = "Flat"
$form.Controls.Add($deleteButton)

$logBox = New-Object System.Windows.Forms.TextBox
$logBox.Multiline = $true
$logBox.ReadOnly = $true
$logBox.ScrollBars = "Vertical"
$logBox.Font = New-Object System.Drawing.Font("Consolas", 9)
$logBox.Location = New-Object System.Drawing.Point(32, 280)
$logBox.Size = New-Object System.Drawing.Size(680, 220)
$logBox.Anchor = "Top, Bottom, Left, Right"
$form.Controls.Add($logBox)
$script:startupProcess = $null
$script:lastLogText = ""
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 1000
$timer.Add_Tick({
    if (-not $script:startupProcess) { return }
    $path = Join-Path $logDir "start-all.log"
    $lines = @(Get-Content -LiteralPath $path -Tail 80 -ErrorAction SilentlyContinue)
    $text = $lines -join [Environment]::NewLine
    if ($text -ne $script:lastLogText) {
        $script:lastLogText = $text
        $logBox.Text = $text
        $logBox.SelectionStart = $logBox.TextLength
        $logBox.ScrollToCaret()
        $latest = $lines | Where-Object { $_ -match '^\[\d{2}:\d{2}:\d{2}\]' } | Select-Object -Last 1
        if ($latest) { $status.Text = $latest }
    }
    if ($script:startupProcess.HasExited) {
        if ($script:startupProcess.ExitCode -eq 0 -and $text -match 'ALL SERVICES ARE READY') {
            $status.Text = "ALL SERVICES ARE READY - http://localhost:3000"
            $status.ForeColor = [System.Drawing.Color]::ForestGreen
        } else {
            $failure = $lines | Where-Object { $_ -match 'STARTUP FAILED:' } | Select-Object -Last 1
            $status.Text = if ($failure) { $failure } else { "Startup failed. See .local-logs/launcher.err.log" }
            $status.ForeColor = [System.Drawing.Color]::Firebrick
        }
        $startButton.Enabled = $true
        $stopButton.Enabled = $true
        $deleteButton.Enabled = $true
        $script:startupProcess = $null
    }
})
$timer.Start()
$form.Add_FormClosed({ $timer.Stop(); $timer.Dispose() })

function Start-StackProcess {
    param(
        [string]$BatchName,
        [string]$StartedMessage
    )
    $status.Text = $StartedMessage
    $batch = Join-Path $repo $BatchName
    Start-Process -FilePath $batch -WorkingDirectory $repo -WindowStyle Hidden
}

$startButton.Add_Click({
    $status.Text = "Starting... live progress appears below."
    $status.ForeColor = [System.Drawing.Color]::FromArgb(91, 56, 134)
    $logBox.Clear()
    $script:lastLogText = ""
    Set-Content -LiteralPath (Join-Path $logDir "start-all.log") -Encoding utf8 -Value ""
    $scriptPath = Join-Path $PSScriptRoot "start-all-local.ps1"
    $script:startupProcess = Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`"" `
        -WorkingDirectory $repo -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logDir "launcher.out.log") `
        -RedirectStandardError (Join-Path $logDir "launcher.err.log")
    $startButton.Enabled = $false
    $stopButton.Enabled = $false
    $deleteButton.Enabled = $false
})

$stopButton.Add_Click({
    Start-StackProcess -BatchName "STOP_VPLMQA_LOCAL.bat" -StartedMessage "Stopping only... containers will be kept."
})

$openButton.Add_Click({
    Start-Process -FilePath "http://localhost:3000"
})

$logsButton.Add_Click({
    Start-Process -FilePath "explorer.exe" -ArgumentList @($logDir)
})

$deleteButton.Add_Click({
    $answer = [System.Windows.Forms.MessageBox]::Show(
        "This will delete the local Docker containers, but keep volumes/data. Continue?",
        "Delete VPLMQA containers",
        [System.Windows.Forms.MessageBoxButtons]::YesNo,
        [System.Windows.Forms.MessageBoxIcon]::Warning
    )
    if ($answer -eq [System.Windows.Forms.DialogResult]::Yes) {
        Start-StackProcess -BatchName "DELETE_VPLMQA_CONTAINERS.bat" -StartedMessage "Deleting containers... volumes/data will be kept."
    }
})

[void]$form.ShowDialog()
