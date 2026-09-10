param([ValidateSet('local','compatible')][string]$Profile = 'local', [string]$Work = '', [string]$Python = '')
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Stand.psm1') -Force
$context = Get-StandContext $Profile $Work
$operation = Enter-StandOperation $context
try {
    $Python = Resolve-StandTool 'python' $Python
    $process = Get-OwnedStandProcess $context 'dependencies'
    if ($process) { Stop-Process -Id $process.ProcessId; Wait-Process -Id $process.ProcessId -Timeout 15 -ErrorAction SilentlyContinue }
    $stub = (Join-Path $PSScriptRoot 'dependency_stub.py').Replace('\','/')
    $null = Start-StandProcess $context 'dependencies' $Python @('-u',('"' + $stub + '"'),'--port',[string]$context.Config.dependencyPort,
        '--state',('"' + (Join-Path $context.Work 'dependency-fixtures.json') + '"'),
        '--journal',('"' + (Join-Path $context.Work 'dependency-requests.jsonl') + '"')) $context.Config.dependencyPort $context.Work
    Wait-StandDependencies $context
    if (Get-OwnedStandProcess $context 'service') {
        $null = Invoke-RestMethod -Uri ($context.ServiceUrl + '/api/v2/data-operator/update-dicts') -Method Put -TimeoutSec 20
    }
    Write-Output ('Dependency stubs UP: http://127.0.0.1:' + $context.Config.dependencyPort)
} finally { $operation.Dispose() }
