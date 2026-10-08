param([Parameter(Mandatory=$true)][string]$Jar)
$ErrorActionPreference = 'Stop'
$jarPath = (Resolve-Path -LiteralPath $Jar).Path.Replace('\','/')
$runtime = Join-Path $PSScriptRoot '.runtime'
$envFile = Join-Path $runtime 'local.env'
if (Test-Path -LiteralPath $envFile) { throw 'Runtime configuration exists. Reuse it; do not rotate credentials on an existing database volume.' }
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
@("LOCAL_DB_PASSWORD=$password", "SCHEDULER_JAR=$jarPath") | Set-Content -LiteralPath $envFile -Encoding ASCII
Write-Output 'Local runtime configuration created. Do not commit or package .runtime.'
