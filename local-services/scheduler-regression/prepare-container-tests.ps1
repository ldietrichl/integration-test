param(
    [ValidateSet('legacy-null','dev-v2')][string]$SchemaProfile='legacy-null',
    [switch]$MutualTls
)
$ErrorActionPreference='Stop'
$runtime=Join-Path $PSScriptRoot '.runtime/full-stack'
$config=Join-Path $runtime 'test-work/src/test/resources'
New-Item -ItemType Directory -Path $config,(Join-Path $runtime 'tls') -Force | Out-Null
$vars=Get-Content -LiteralPath (Join-Path $PSScriptRoot '.runtime/local.env') | ConvertFrom-StringData
if (!$vars.LOCAL_DB_PASSWORD) { throw 'Prepare the isolated database runtime first' }
$port=if($MutualTls){8444}else{8443}
# Disposable local identity only; never used for corporate authentication.
$token='eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJsb2NhbDEwMSJ9.'
$properties=@(
    'env=local', "scheduler.local.base-uri=https://tls-proxy:$port",
    'scheduler.local.user-id=101', 'scheduler.local.other-user-id=102',
    'scheduler.fixtures.enabled=true', 'scheduler.fixtures.isolated=true', 'scheduler.jobs.paused=true',
    "scheduler.schema.profile=$SchemaProfile", 'scheduler.timeout.seconds=15',
    'scheduler.local.expected-creator.employee-id=LOCAL101',
    'scheduler.local.expected-creator.email=local101@example.test',
    'scheduler.local.splitting-point-name=Local mapper point', "scheduler.local.token=$token",
    'scheduler.local.truststore=/runtime/tls/truststore.p12', 'scheduler.local.truststore.type=PKCS12',
    'truststore.pass=${SECURE_LOCAL_TRUST_PASS}', 'keystore.pass=${SECURE_LOCAL_TRUST_PASS}',
    ('scheduler.local.mtls.enabled='+$MutualTls.IsPresent.ToString().ToLowerInvariant()),
    'scheduler.local.keystore=/runtime/tls/client.p12', 'scheduler.local.keystore.type=PKCS12'
)
$properties | Set-Content -LiteralPath (Join-Path $config 'test.properties') -Encoding ASCII
@('db.local.explab.url=jdbc:postgresql://database:5432/scheduler', 'db.local.explab.login=scheduler',
    "db.local.explab.password=$($vars.LOCAL_DB_PASSWORD)", 'db.local.explab.timeout.in.seconds=15',
    'db.local.explab.connection.pool.size=2') |
    Set-Content -LiteralPath (Join-Path $config 'database.properties') -Encoding ASCII
"SECURE_LOCAL_TRUST_PASS=$($vars.LOCAL_DB_PASSWORD)" |
    Set-Content -LiteralPath (Join-Path $runtime 'test-work/secure.local.override.properties') -Encoding ASCII
Write-Output 'Local container test configuration prepared; shared corporate properties unchanged.'
