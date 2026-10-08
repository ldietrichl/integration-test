param([ValidateSet('local','compatible')][string]$Profile = 'local', [string]$Work = '', [string]$Run = '', [string]$Allure = '')
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Stand.psm1') -Force
$context = Get-StandContext $Profile $Work
$operation = Enter-StandOperation $context
try {
    if (-not $Run) {
        $Run = Get-ChildItem -LiteralPath $context.Work -Directory -Filter 'run-*' | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'analysis.json') } | Sort-Object CreationTimeUtc -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $Run) { throw 'No completed run available' }
    $Run = [IO.Path]::GetFullPath($Run)
    if (-not $Run.StartsWith($context.Work + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Report must belong to this stand' }
    if (-not $Allure) {
        $Allure = Join-Path $context.Back '01_reference_sources/input_test_project/integration-test/allure/bin/allure.bat'
        if (-not (Test-Path -LiteralPath $Allure)) { $Allure = Resolve-StandTool 'allure' }
    }
    $report = Join-Path $Run 'allure-report'
    if (-not (Test-Path -LiteralPath (Join-Path $report 'index.html'))) {
        & $Allure generate (Join-Path $Run 'allure-results') -o $report
        if ($LASTEXITCODE -ne 0) { throw 'Allure generation failed' }
    }
    $existing = Get-OwnedStandProcess $context report
    if ($existing -and -not $existing.CommandLine.Replace('\','/').Contains($report.Replace('\','/'))) { Stop-Process -Id $existing.ProcessId }
    $python = Resolve-StandTool python
    $null = Start-StandProcess $context report $python @('-u','-m','http.server',[string]$context.Config.reportPort,'--bind','127.0.0.1','--directory',('"' + $report + '"')) $context.Config.reportPort $context.Work
    Write-Output ('Allure: http://127.0.0.1:' + $context.Config.reportPort)
} finally { $operation.Dispose() }
