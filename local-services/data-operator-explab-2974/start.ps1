param(
    [ValidateSet('local','compatible')][string]$Profile = 'local', [string]$Work = '',
    [string]$Source = '', [string]$Python = '', [string]$Java = '', [switch]$Pull
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Stand.psm1') -Force
$context = Get-StandContext $Profile $Work
$operation = Enter-StandOperation $context
try {
    $Python = Resolve-StandTool 'python' $Python
    $Java = Resolve-StandTool 'java' $Java
    if (-not $Source) { $Source = $context.Source }
    $build = Join-Path $context.Work 'service'
    if (-not (Test-Path -LiteralPath $build)) {
        & $Python (Join-Path $PSScriptRoot 'prepare_service.py') --source $Source --work $context.Work --ignite-version $context.Config.igniteVersion
        if ($LASTEXITCODE -ne 0) { throw 'Service source preparation failed' }
    }
    & $Python (Join-Path $PSScriptRoot 'verify_service.py') --work $context.Work --ignite-version $context.Config.igniteVersion
    if ($LASTEXITCODE -ne 0) { throw 'Service copy differs from its source manifest' }
    $jar = Join-Path $build 'target/data-operator-0.0.1-SNAPSHOT.jar'
    $buildStamp = Join-Path $context.Work 'service-build.json'
    $pomHash = (Get-FileHash -LiteralPath (Join-Path $build 'pom.xml') -Algorithm SHA256).Hash
    $reuse = $false
    if ((Test-Path -LiteralPath $jar) -and (Test-Path -LiteralPath $buildStamp)) {
        $stamp = Get-Content -LiteralPath $buildStamp -Raw | ConvertFrom-Json
        $reuse = $stamp.pomSha256 -eq $pomHash -and $stamp.jarSha256 -eq (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    }
    if (-not $reuse) {
        if (Get-OwnedStandProcess $context 'service') { throw 'Stop this stand before rebuilding the service' }
        $maven = Resolve-StandTool 'mvn'
        & $maven -B -ntp -s (Join-Path $context.Work 'maven-settings.xml') -f (Join-Path $build 'pom.xml') '-Dmaven.test.skip=true' package *> (Join-Path $context.Work 'build-service.log')
        if ($LASTEXITCODE -ne 0) { throw 'Service build failed; see build-service.log' }
        Write-StandJson $buildStamp @{pomSha256=$pomHash; jarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash}
    }
    if ($Pull) { Invoke-StandCompose $context @('pull') }
    Invoke-StandCompose $context @('up','-d')
    $stub = (Join-Path $PSScriptRoot 'dependency_stub.py').Replace('\','/')
    $null = Start-StandProcess $context 'dependencies' $Python @('-u',('"' + $stub + '"'),'--port',[string]$context.Config.dependencyPort,
        '--state',('"' + (Join-Path $context.Work 'dependency-fixtures.json') + '"'),
        '--journal',('"' + (Join-Path $context.Work 'dependency-requests.jsonl') + '"')) $context.Config.dependencyPort $context.Work
    Wait-StandDependencies $context
    if (-not (Get-OwnedStandProcess $context 'service')) {
        $config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'application-local.properties') -Raw
        foreach ($entry in @{SERVICE_PORT=$context.Config.servicePort; DEPENDENCY_PORT=$context.Config.dependencyPort; KAFKA_PORT=$context.Config.kafkaPort; IGNITE_PORT=$context.Config.ignitePort; PROJECT=$context.Config.project}.GetEnumerator()) { $config = $config.Replace(('${' + $entry.Key + '}'),[string]$entry.Value) }
        $configFile = (Join-Path $context.Work 'application-local.properties').Replace('\','/')
        [IO.File]::WriteAllText($configFile,$config,[Text.UTF8Encoding]::new($false))
        $arguments = @('-Xms256m','-Xmx768m','-XX:ActiveProcessorCount=4','-Dfile.encoding=UTF-8')
        foreach ($path in @('java.nio','sun.nio.ch','sun.nio.cs','java.lang','java.lang.invoke','java.lang.reflect','java.util','java.io')) { $arguments += '--add-opens=java.base/' + $path + '=ALL-UNNAMED' }
        $arguments += @('-jar',$jar.Replace('\','/'),('--spring.config.additional-location=file:' + $configFile))
        $argumentFile = Join-Path $context.Work 'service.args'
        [IO.File]::WriteAllLines($argumentFile,@($arguments | ForEach-Object { '"' + $_ + '"' }),[Text.UTF8Encoding]::new($false))
        $null = Start-StandProcess $context 'service' $Java @('"@' + $argumentFile + '"') $context.Config.servicePort $build
    }
    Wait-StandReady $context
    Write-Output ('Data operator is UP: ' + $context.ServiceUrl)
} finally { $operation.Dispose() }
