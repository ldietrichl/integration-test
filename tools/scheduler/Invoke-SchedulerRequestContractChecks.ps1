#requires -Version 5.1
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Project)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath($Project)
$wrapper = Join-Path $projectRoot 'gradlew.bat'
$initScript = Join-Path $PSScriptRoot 'scheduler-local-contract-checks.init.gradle'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw 'Project gradlew.bat is missing.' }
if (-not (Test-Path -LiteralPath $initScript -PathType Leaf)) { throw 'Local contract init script is missing.' }
Push-Location -LiteralPath $projectRoot
try {
    & $wrapper --offline --no-daemon --no-configuration-cache --console=plain --rerun-tasks -I $initScript schedulerLocalRequestContractChecks
    if ($LASTEXITCODE -ne 0) { throw ('Local guard checks failed, exit code ' + $LASTEXITCODE) }
    Write-Output 'LocalGuardValidationPassed=true'
} finally { Pop-Location }
