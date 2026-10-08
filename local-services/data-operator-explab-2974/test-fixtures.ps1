param([ValidateSet('local','compatible')][string]$Profile = 'compatible', [string]$Work = '',
    [string]$Python = '', [string]$Java = '', [string]$Javac = '', [string]$Libraries = '')
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
    Wait-StandDependencies $context
    Wait-StandReady $context
    $outputDirectory = Join-Path $context.Work ('run-fixture-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
    Write-StandJson (Join-Path $outputDirectory 'pre-run-state.json') (Get-StandDataState $context)
    & $Python (Join-Path $PSScriptRoot 'compile_tests.py') --work $context.Work --libs $Libraries --javac $Javac
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    $fixtureManifest = Join-Path $outputDirectory 'fixture-manifest.json'
    $serviceLog = Join-Path $context.Work 'service.stdout.log'
    $startLine = @(Get-Content -LiteralPath $serviceLog).Count
    try {
        & $Python (Join-Path $PSScriptRoot 'fixture_session.py') prepare --work $context.Work --manifest $fixtureManifest --java $Java --javac $Javac
        if ($LASTEXITCODE -ne 0) { throw 'Fixture preparation failed; cleanup will run using the saved manifest' }
        $manifestPath = Join-Path $outputDirectory 'run-manifest.json'
        Write-StandJson $manifestPath @{schemaVersion=1; suite='fixture'; fixtureManifest=$fixtureManifest;
            profile=$context.Profile; config=$context.Config; serviceUrl=$context.ServiceUrl;
            serviceBuild=(Get-Content (Join-Path $context.Work 'service-build.json') -Raw | ConvertFrom-Json);
            testBuild=(Get-Content (Join-Path $context.Work 'test-build.json') -Raw | ConvertFrom-Json)}
        $classpath = [IO.File]::ReadAllText((Join-Path $context.Work 'test-classpath.txt'))
        $arguments = @('-Dfile.encoding=UTF-8','-cp',$classpath,'LocalScenarioRunner',$outputDirectory.Replace('\','/'),$manifestPath.Replace('\','/'))
        $argumentFile = Join-Path $outputDirectory 'java.args'
        [IO.File]::WriteAllLines($argumentFile,@($arguments | ForEach-Object { '"' + $_ + '"' }),[Text.UTF8Encoding]::new($false))
        & $Java ('@' + $argumentFile) *> (Join-Path $outputDirectory 'scenarios.log')
        $testExit = $LASTEXITCODE
    } finally {
        try {
            if (Test-Path -LiteralPath $fixtureManifest) {
                & $Python (Join-Path $PSScriptRoot 'fixture_session.py') cleanup --manifest $fixtureManifest
                if ($LASTEXITCODE -ne 0) { throw 'Fixture cleanup failed; retain fixture-manifest.json for recovery' }
            }
        } finally {
            Write-StandJson (Join-Path $outputDirectory 'post-run-state.json') (Get-StandDataState $context)
            [IO.File]::WriteAllLines((Join-Path $outputDirectory 'service-test-interval.log'),@(Get-Content -LiteralPath $serviceLog | Select-Object -Skip $startLine),[Text.UTF8Encoding]::new($false))
            Write-Output ('Fixture results: ' + $outputDirectory)
        }
    }
    & $Python (Join-Path $PSScriptRoot 'analyze_run.py') --run $outputDirectory
    if ($LASTEXITCODE -ne 0) { $testExit = 2 }
    Get-Content -LiteralPath (Join-Path $outputDirectory 'scenarios.log') -Tail 14
} finally { $operation.Dispose() }
exit $testExit
