Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$standRoot = $PSScriptRoot

function Write-StandJson($Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
}

function Get-StandContext([string]$Profile = 'local', [string]$Work = '') {
    $profiles = Get-Content -LiteralPath (Join-Path $standRoot 'profiles.json') -Raw | ConvertFrom-Json
    if ($Profile -notin @($profiles.PSObject.Properties.Name)) { throw "Unknown profile: $Profile" }
    $config = $profiles.$Profile
    $ports = @($config.servicePort,$config.dependencyPort,$config.ignitePort,$config.kafkaPort,$config.reportPort)
    if (@($ports | Select-Object -Unique).Count -ne 5 -or @($ports | Where-Object { $_ -lt 1024 -or $_ -gt 65535 }).Count) { throw 'Profile ports must be distinct and within 1024..65535' }
    $repo = Split-Path (Split-Path $standRoot -Parent) -Parent
    $back = Join-Path (Split-Path $repo -Parent) 'BACK'
    if (-not $Work) { $Work = Join-Path $back ('06_temp_work/' + $config.project) }
    $Work = [IO.Path]::GetFullPath($Work)
    $identity = Join-Path $Work 'stand.json'
    if (Test-Path -LiteralPath $identity) {
        $saved = Get-Content -LiteralPath $identity -Raw | ConvertFrom-Json
        if ($saved.profile -ne $Profile -or $saved.work -ne $Work -or ($saved.config | ConvertTo-Json -Compress) -ne ($config | ConvertTo-Json -Compress)) {
            throw 'Work directory belongs to a different profile/configuration. Select its profile or a new Work directory.'
        }
    }
    [pscustomobject]@{ Profile=$Profile; Config=$config; Work=$Work; Root=$standRoot; Repo=$repo; Back=$back
        ServiceUrl=('http://127.0.0.1:' + $config.servicePort)
        Source=(Join-Path $back '01_reference_sources/data_operator_service/EXPLAB-2974_06fd6acb646')
        Libraries=(Join-Path $back '01_reference_sources/lib-1.10.3-beta') }
}

function Enter-StandOperation($Context) {
    New-Item -ItemType Directory -Path $Context.Work -Force | Out-Null
    try { $lock = [IO.File]::Open((Join-Path $Context.Work 'operation.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
    catch { throw 'Another start/stop/test/report operation is using this stand' }
    try {
        Write-StandJson (Join-Path $Context.Work 'stand.json') @{schemaVersion=1; profile=$Context.Profile; work=$Context.Work; config=$Context.Config}
        return $lock
    } catch { $lock.Dispose(); throw }
}

function Resolve-StandTool([string]$Name, [string]$Explicit = '') {
    if ($Explicit) { return (Get-Command $Explicit -ErrorAction Stop).Source }
    $override = [Environment]::GetEnvironmentVariable('EXPLAB_' + $Name.ToUpperInvariant())
    if ($override) { return (Get-Command $override -ErrorAction Stop).Source }
    if ($Name -eq 'python') {
        $bundled = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
        if (Test-Path -LiteralPath $bundled) { return $bundled }
    }
    if ($Name -in @('java','javac') -and $env:JAVA_HOME) {
        $jdkTool = Join-Path $env:JAVA_HOME ('bin/' + $Name + '.exe')
        if (Test-Path -LiteralPath $jdkTool) { return $jdkTool }
    }
    return (Get-Command $Name -ErrorAction Stop).Source
}

function Invoke-StandCompose($Context, [string[]]$Command) {
    $values = @{COMPOSE_PROJECT_NAME=$Context.Config.project; IGNITE_VERSION=$Context.Config.igniteVersion;
        KAFKA_VERSION=$Context.Config.kafkaVersion; IGNITE_PORT=[string]$Context.Config.ignitePort; KAFKA_PORT=[string]$Context.Config.kafkaPort}
    $before = @{}
    foreach ($key in $values.Keys) { $before[$key] = [Environment]::GetEnvironmentVariable($key); [Environment]::SetEnvironmentVariable($key,$values[$key]) }
    try {
        & docker compose --project-name $Context.Config.project -f (Join-Path $standRoot 'compose.yaml') @Command
        if ($LASTEXITCODE -ne 0) { throw ('Docker Compose failed: ' + ($Command -join ' ')) }
    } finally { foreach ($key in $before.Keys) { [Environment]::SetEnvironmentVariable($key,$before[$key]) } }
}

function Get-ProcessMarker($Context, [string]$Component) {
    switch ($Component) {
        'service' { (Join-Path $Context.Work 'service.args').Replace('\','/') }
        'dependencies' { (Join-Path $Context.Root 'dependency_stub.py').Replace('\','/') }
        'report' { '--directory' }
        default { throw 'Unknown stand process' }
    }
}

function Get-OwnedStandProcess($Context, [string]$Component) {
    $recordPath = Join-Path $Context.Work ($Component + '.process.json')
    if (Test-Path -LiteralPath $recordPath) {
        $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
        $running = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$record.pid)"
        if (-not $running) { return $null }
        if ($running.CreationDate.ToUniversalTime() -ne ([DateTime]$record.createdAt).ToUniversalTime() -or $running.CommandLine -ne $record.commandLine) {
            throw "Saved $Component PID now belongs to another process; it will not be used or stopped"
        }
        if (-not $running.CommandLine.Replace('\','/').Contains((Get-ProcessMarker $Context $Component))) { throw 'Process identity mismatch' }
        return $running
    }
    $legacy = Join-Path $Context.Work ($Component + '.pid')
    if (-not (Test-Path -LiteralPath $legacy)) { return $null }
    $legacyId = [int](Get-Content -LiteralPath $legacy)
    $running = Get-CimInstance Win32_Process -Filter "ProcessId=$legacyId"
    if (-not $running) { return $null }
    $line = ([string]$running.CommandLine).Replace('\','/')
    if (-not $line.Contains((Get-ProcessMarker $Context $Component))) { throw "Unverified legacy $Component PID" }
    if ($Component -eq 'dependencies' -and $Context.Profile -ne 'local' -and $line -notmatch ('--port\s+' + $Context.Config.dependencyPort + '(?:\s|$)')) { throw 'Legacy dependency port mismatch' }
    if ($Component -eq 'report' -and -not $line.Contains($Context.Work.Replace('\','/'))) { throw 'Legacy report directory mismatch' }
    Save-StandProcess $Context $Component $running
    return $running
}

function Save-StandProcess($Context, [string]$Component, $Process) {
    Write-StandJson (Join-Path $Context.Work ($Component + '.process.json')) @{pid=$Process.ProcessId; createdAt=$Process.CreationDate.ToUniversalTime().ToString('o'); commandLine=$Process.CommandLine}
}

function Test-StandPort([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try { return $client.ConnectAsync('127.0.0.1',$Port).Wait(300) -and $client.Connected }
    catch { return $false } finally { $client.Dispose() }
}

function Start-StandProcess($Context, [string]$Component, [string]$Executable, [string[]]$Arguments, [int]$Port, [string]$Directory) {
    $existing = Get-OwnedStandProcess $Context $Component
    if ($existing) { return $existing }
    if (Test-StandPort $Port) { throw "Port $Port is occupied by an unmanaged process" }
    foreach ($stream in @('stdout','stderr')) {
        $log = Join-Path $Context.Work ($Component + '.' + $stream + '.log')
        if (Test-Path -LiteralPath $log) { Copy-Item -LiteralPath $log -Destination ($log + '.' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.bak') }
    }
    $started = Start-Process -FilePath $Executable -ArgumentList $Arguments -WorkingDirectory $Directory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Context.Work ($Component + '.stdout.log')) -RedirectStandardError (Join-Path $Context.Work ($Component + '.stderr.log'))
    $actual = Get-CimInstance Win32_Process -Filter "ProcessId=$($started.Id)"
    if (-not $actual) { throw "$Component exited at startup; inspect its logs" }
    Save-StandProcess $Context $Component $actual
    return $actual
}

function Wait-StandDependencies($Context) {
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        if (-not (Get-OwnedStandProcess $Context 'dependencies')) { throw 'Dependency process exited; inspect its logs' }
        $health = $null
        try { $health = Invoke-RestMethod -Uri ('http://127.0.0.1:' + $Context.Config.dependencyPort + '/health') -TimeoutSec 2 } catch { }
        if ($health) {
            if ($health.PSObject.Properties.Name -notcontains 'version' -or $health.version -ne 'EXPLAB-2974-D1-v2') { throw 'Old dependency stub is running; use restart-dependencies.ps1 for this profile' }
            if ($health.status -eq 'UP') { return }
        }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Dependency contract stub did not become ready'
}

function Wait-StandReady($Context) {
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        if (-not (Get-OwnedStandProcess $Context 'service') -or -not (Get-OwnedStandProcess $Context 'dependencies')) { throw 'A managed process exited; inspect service/dependency logs' }
        try {
            $health = Invoke-RestMethod -Uri ($Context.ServiceUrl + '/actuator/health') -TimeoutSec 3
            $metadata = Invoke-RestMethod -Uri ($Context.ServiceUrl + '/api/v2/data-operator/caches/param_cache/entries?limit=1') -TimeoutSec 3
            if ($health.status -eq 'UP' -and $metadata.count -gt 0) { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Stand did not become ready; inspect service.stdout.log and dependency logs'
}

function Get-StandDataState($Context) {
    $state = @{health=$null; caches=@{}; errors=@()}
    try { $state.health = Invoke-RestMethod -Uri ($Context.ServiceUrl + '/actuator/health') -TimeoutSec 5 } catch { $state.errors += 'Service health unavailable' }
    foreach ($cache in @('splitting_object_cache','splitting_field_cache','actualization_cache','param_cache','data_source_cache')) {
        try { $state.caches[$cache] = (Invoke-RestMethod -Uri ($Context.ServiceUrl + '/api/v2/data-operator/caches/' + $cache + '/entries?limit=1') -TimeoutSec 5).count }
        catch { $state.errors += ('Cache unavailable: ' + $cache) }
    }
    return $state
}

Export-ModuleMember -Function *-Stand*,Get-OwnedStandProcess,Save-StandProcess
