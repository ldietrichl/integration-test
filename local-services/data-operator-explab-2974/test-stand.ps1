$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Stand.psm1') -Force
$testDirectory = Join-Path ([IO.Path]::GetTempPath()) ('explab2974-stand-' + [Guid]::NewGuid().ToString('N'))
$context = Get-StandContext compatible $testDirectory
$checks = 0
function Expect-Failure([scriptblock]$Action,[string]$Message) {
    $failed = $false
    try { & $Action | Out-Null } catch { $failed = $_.Exception.Message.Contains($Message) }
    if (-not $failed) { throw ('Expected rejection: ' + $Message) }
}
try {
    $operation = Enter-StandOperation $context
    try { Expect-Failure { Enter-StandOperation $context } 'Another'; $checks++ } finally { $operation.Dispose() }
    Expect-Failure { Get-StandContext local $testDirectory } 'different profile'; $checks++
    $current = Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
    Write-StandJson (Join-Path $context.Work 'service.process.json') @{pid=$PID; createdAt=$current.CreationDate.AddDays(-1).ToString('o'); commandLine=$current.CommandLine}
    Expect-Failure { Get-OwnedStandProcess $context service } 'another process'; $checks++
    if (-not (Get-Process -Id $PID)) { throw 'Unrelated process was stopped' }
    $guard = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    $guard.Start()
    try {
        Expect-Failure { Start-StandProcess $context dependencies 'unused.exe' @() $guard.LocalEndpoint.Port $context.Work } 'occupied by an unmanaged process'
        $checks++
    } finally { $guard.Stop() }
    Write-Output ("Stand regression checks passed: $checks")
} finally {
    # Only known files from this invocation; no recursive delete.
    foreach ($file in @('operation.lock','stand.json','service.process.json')) {
        $target = Join-Path $testDirectory $file
        if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target }
    }
    if (Test-Path -LiteralPath $testDirectory) { Remove-Item -LiteralPath $testDirectory }
}
