param([ValidateSet('local','compatible')][string]$Profile = 'compatible', [string]$Work = '',
    [Parameter(Mandatory)][string]$Manifest, [string]$Python = '')
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Stand.psm1') -Force
$context = Get-StandContext $Profile $Work
$operation = Enter-StandOperation $context
try {
    $resolved = (Resolve-Path -LiteralPath $Manifest).Path
    if (-not $resolved.StartsWith($context.Work.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Fixture manifest must be inside the selected stand Work directory'
    }
    $saved = Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json
    if ($saved.serviceUrl -ne $context.ServiceUrl -or $saved.igniteAddress -ne ('127.0.0.1:' + $context.Config.ignitePort) -or
        $saved.stubUrl -ne ('http://127.0.0.1:' + $context.Config.dependencyPort)) { throw 'Fixture belongs to a different stand' }
    $Python = Resolve-StandTool 'python' $Python
    & $Python (Join-Path $PSScriptRoot 'fixture_session.py') cleanup --manifest $resolved
    if ($LASTEXITCODE -ne 0) { throw 'Cleanup incomplete; retain the manifest and inspect cache-tool logs' }
} finally { $operation.Dispose() }
