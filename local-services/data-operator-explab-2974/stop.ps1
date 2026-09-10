param([ValidateSet('local','compatible')][string]$Profile = 'local', [string]$Work = '')
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Stand.psm1') -Force
$context = Get-StandContext $Profile $Work
$operation = Enter-StandOperation $context
try {
    $owned = @{}
    foreach ($component in @('report','service','dependencies')) { $owned[$component] = Get-OwnedStandProcess $context $component }
    foreach ($component in @('report','service','dependencies')) {
        if ($owned[$component]) { Stop-Process -Id $owned[$component].ProcessId -ErrorAction Stop; Write-Output ('Stopped ' + $component) }
    }
    Invoke-StandCompose $context @('stop')
} finally { $operation.Dispose() }
