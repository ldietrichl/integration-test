#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ReportPath,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-f0-9]{32}$')][string]$Invocation
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
    throw 'INCOMPLETE_HTTP_DIAGNOSTICS: the current invocation produced no completion receipt.'
}
try { $report = Get-Content -LiteralPath $ReportPath -Raw -Encoding UTF8 | ConvertFrom-Json }
catch { throw 'INCOMPLETE_HTTP_DIAGNOSTICS: completion receipt is not valid JSON.' }
$testClass = 'ru.sber.qa.scheduler.infrastructure.SchedulerHttpDiagnosticFlowTest'
if ($report.schemaVersion -ne 1 -or $report.invocation -cne $Invocation -or
    $report.testClass -cne $testClass -or $report.source -cne 'GRADLE_TEST_LISTENER' -or
    $report.runComplete -isnot [bool] -or $report.allPassed -isnot [bool]) {
    throw 'INCOMPLETE_HTTP_DIAGNOSTICS: stale or incompatible receipt.'
}
$observations = @($report.observations | Where-Object { $null -ne $_ })
$expected = @('http001','http002','http003')
$known = $true
foreach ($item in $observations) {
    if ($item.className -cne $testClass -or $expected -cnotcontains $item.method -or
        @('SUCCESS','FAILURE','SKIPPED') -cnotcontains $item.resultType) { $known = $false }
}
$unique = $observations.Count -eq 3
foreach ($id in $expected) {
    if (@($observations | Where-Object { $_.method -ceq $id }).Count -ne 1) { $unique = $false }
}
$skipped = @($observations | Where-Object { $_.resultType -ceq 'SKIPPED' }).Count
$failed = @($observations | Where-Object { $_.resultType -ceq 'FAILURE' }).Count
$complete = $known -and $unique -and $skipped -eq 0
$passed = $complete -and $failed -eq 0
if ($report.observedCount -ne $observations.Count -or $report.skipped -ne $skipped -or
    $report.failed -ne $failed -or $report.runComplete -ne $complete -or $report.allPassed -ne $passed) {
    throw 'INCOMPLETE_HTTP_DIAGNOSTICS: inconsistent completion counters.'
}
Write-Output ('HttpDiagnosticsExpected=3; Executed=' + $observations.Count + '; Skipped=' + $skipped + '; Failed=' + $failed)
Write-Output ('HttpDiagnosticsRunComplete=' + $complete)
Write-Output ('HttpDiagnosticsAllPassed=' + $passed)
if (-not $complete) { throw 'INCOMPLETE_HTTP_DIAGNOSTICS: all three expected methods must execute exactly once, without skips.' }
if (-not $passed) { throw 'HTTP_DIAGNOSTICS_FAILED: all three executed; preserve the failed service/transport assertions.' }
