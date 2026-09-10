param(
    [ValidateSet('local','compatible')][string]$Profile = 'local', [string]$Work = '',
    [string]$Libraries = '', [string]$Python = '', [string]$Java = '', [string]$Javac = '',
    [string]$BaselineResults = ''
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Stand.psm1') -Force
$context = Get-StandContext $Profile $Work
$operation = Enter-StandOperation $context
$testExit = 2
try {
    $Python = Resolve-StandTool 'python' $Python
    $Java = Resolve-StandTool 'java' $Java
    $Javac = Resolve-StandTool 'javac' $Javac
    if (-not $Libraries) { $Libraries = $context.Libraries }
    Wait-StandReady $context
    $outputDirectory = Join-Path $context.Work ('run-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
    $before = Get-StandDataState $context
    Write-StandJson (Join-Path $outputDirectory 'pre-run-state.json') $before
    if ($before.errors.Count -or @('splitting_object_cache','splitting_field_cache','actualization_cache').Where({ $before.caches[$_] -ne 0 }).Count) { throw 'Request-only suite requires healthy infrastructure and empty object caches; existing data will not be deleted' }
    & $Python (Join-Path $PSScriptRoot 'compile_tests.py') --work $context.Work --libs $Libraries --javac $Javac
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    $manifestPath = Join-Path $outputDirectory 'run-manifest.json'
    $images = @()
    foreach ($component in @('ignite','kafka')) {
        $container = (& docker inspect ($context.Config.project + '-' + $component + '-1') | ConvertFrom-Json)[0]
        $images += @{component=$component; image=$container.Config.Image; imageId=$container.Image; status=$container.State.Status}
    }
    Write-StandJson $manifestPath @{schemaVersion=1; profile=$context.Profile; config=$context.Config; serviceUrl=$context.ServiceUrl;
        dependencies='Controlled dictionary/experiment stubs'; images=$images;
        serviceBuild=(Get-Content (Join-Path $context.Work 'service-build.json') -Raw | ConvertFrom-Json);
        testBuild=(Get-Content (Join-Path $context.Work 'test-build.json') -Raw | ConvertFrom-Json)}
    $classpath = [IO.File]::ReadAllText((Join-Path $context.Work 'test-classpath.txt'))
    $arguments = @('-Dfile.encoding=UTF-8','-cp',$classpath,'LocalScenarioRunner',$outputDirectory.Replace('\','/'),$manifestPath.Replace('\','/'))
    $argumentFile = Join-Path $outputDirectory 'java.args'
    [IO.File]::WriteAllLines($argumentFile,@($arguments | ForEach-Object { '"' + $_ + '"' }),[Text.UTF8Encoding]::new($false))
    $serviceLog = Join-Path $context.Work 'service.stdout.log'
    $startLine = @(Get-Content -LiteralPath $serviceLog).Count
    try {
        & $Java ('@' + $argumentFile) *> (Join-Path $outputDirectory 'scenarios.log')
        $testExit = $LASTEXITCODE
    } finally {
        Write-StandJson (Join-Path $outputDirectory 'post-run-state.json') (Get-StandDataState $context)
        Copy-Item -LiteralPath $serviceLog -Destination (Join-Path $outputDirectory 'service.stdout.log')
        [IO.File]::WriteAllLines((Join-Path $outputDirectory 'service-test-interval.log'),@(Get-Content -LiteralPath $serviceLog | Select-Object -Skip $startLine),[Text.UTF8Encoding]::new($false))
    }
    $analysisOptions = @((Join-Path $PSScriptRoot 'analyze_run.py'),'--run',$outputDirectory)
    if ($BaselineResults) { $analysisOptions += @('--baseline',$BaselineResults) }
    & $Python @analysisOptions
    if ($LASTEXITCODE -ne 0) { $testExit = 2 }
    Get-Content -LiteralPath (Join-Path $outputDirectory 'scenarios.log') -Tail 14
    Write-Output ('Results: ' + $outputDirectory)
} finally { $operation.Dispose() }
exit $testExit
