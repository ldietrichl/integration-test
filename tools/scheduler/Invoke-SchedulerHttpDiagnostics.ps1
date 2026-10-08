#requires -Version 5.1
[CmdletBinding()]
param([string]$Project = 'C:\Work\IdeaProjects\integration-test')
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($Project).TrimEnd('\','/')
$wrapper = Join-Path $root 'gradlew.bat'
$init = Join-Path $PSScriptRoot 'scheduler-http-diagnostics.init.gradle'
$checker = Join-Path $PSScriptRoot 'Assert-SchedulerHttpDiagnostics.ps1'
foreach ($path in @($wrapper,$init,$checker)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw ('Required diagnostic launcher file is missing: ' + $path) }
}
$invocation = [guid]::NewGuid().ToString('N')
$receiptPath = $null
$receiptPrefix = 'SchedulerHttpGuardReceipt='
$code = -1
Write-Output 'Mode=ReadOnlyHttpDiagnostics; Requests=RegistryQueries; Mutations=false'
Write-Output ('ExpectedTests=http001,http002,http003; Invocation=' + $invocation)
Push-Location -LiteralPath $root
try {
    $previousPreference = $ErrorActionPreference
    try {
        # Native stderr is not proof of failure. Preserve output and use the Gradle exit code.
        $ErrorActionPreference = 'Continue'
        & $wrapper -I $init ('-PschedulerHttpDiagnosticsInvocation=' + $invocation) schedulerInfrastructureDiagnostics --tests 'ru.sber.qa.scheduler.infrastructure.SchedulerHttpDiagnosticFlowTest' --no-daemon --no-configuration-cache --console=plain --rerun-tasks 2>&1 |
            ForEach-Object {
                $line = $_.ToString()
                Write-Output $line
                if ($line.StartsWith($receiptPrefix)) { $receiptPath = $line.Substring($receiptPrefix.Length).Trim() }
            }
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
} finally { Pop-Location }
if ([string]::IsNullOrWhiteSpace($receiptPath)) {
    throw ('INCOMPLETE_HTTP_DIAGNOSTICS: no receipt for this invocation; Gradle exit code ' + $code + '. Do not use an older run summary.')
}
$receiptPath = [IO.Path]::GetFullPath($receiptPath)
if (-not $receiptPath.StartsWith($root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
    [IO.Path]::GetFileName($receiptPath) -cne ('http-completion-' + $invocation + '.json')) {
    throw 'INCOMPLETE_HTTP_DIAGNOSTICS: unexpected receipt path. This launcher requires build output inside the project.'
}
$validationFailure = $null
try { & $checker -ReportPath $receiptPath -Invocation $invocation }
catch { $validationFailure = $_.Exception.Message }
if ($code -ne 0) {
    throw ('Gradle diagnostics failed, exit code ' + $code + '. ' + $validationFailure + ' Preserve JUnit/Allure and the completion receipt.')
}
if ($null -ne $validationFailure) { throw $validationFailure }
Write-Output 'SchedulerHttpDiagnosticsPassed=true'
