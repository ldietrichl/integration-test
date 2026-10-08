# Scheduler container regression with approved dependency fixtures

## Scope

Real components: archived scheduler JAR, PostgreSQL 16, HTTPS/mTLS termination and
the Gradle 8.4 container executing the project's compiled JUnit tests and resolved
Platform V AT runtime. External dictionary/user/communication/experiment routes
are SOURCE-DERIVED CONTRACT FIXTURES, not real provider services. Unknown routes
return 501 and every fixture request is recorded without authorization headers.
The communication operator response is an empty local fixture, not a recovered
complete dev dictionary. Worker-dependent cases are not certified by this setup.

Both v1/v2 workers, cleanup and credential rotation are disabled for safe API/DB
fixtures. The 68 managed/fault tests still require operator preparation and fresh
observations; they are not silently claimed as automated or passing.
The real archived service is not patched to make contract assertions pass.

## 1. Prerequisites and scoped compilation

Work in the integration-test project root. Docker Desktop must use Linux containers.
The existing compose.local.yml and prepare-local.ps1 are used to prepare the JAR
path and generated DB password; see LOCAL-CONTAINERS.md for source/JAR packaging.
Never rotate that password while retaining its PostgreSQL volume.

```powershell
$gradle = '.\gradlew.bat'
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
$temp = '<workspace>\BACK\06_temp_work\scheduler-regression-artifacts-20260912'
& $gradle --offline --no-daemon --console=plain `
  -I "$temp/compile-local.init.gradle" -I "$temp/compile-scheduler-scope.init.gradle" `
  -I local-services/scheduler-regression/export-container.init.gradle `
  -PuseLocalLibs=true '-PlocalLibDir=<workspace>/BACK/01_reference_sources/lib' `
  schedulerExportContainerRuntime
if ($LASTEXITCODE -ne 0) { throw 'Compilation/runtime export failed' }
```

This is the REAL project build and dependency resolution. The local diagnostic
scope still excludes exactly three unrelated optional integration sources listed
in LOCAL-CONTAINERS.md because their dependencies are unavailable offline.
It is NOT proof that the unmodified entire corporate project builds offline.
The corporate patch does not carry that exclusion or the local mirror.

Compilation runs with the host's Gradle 8.4; JUnit execution runs inside the
Gradle 8.4/JDK 17 container. The container driver only selects existing project
classes and classpath, not reimplementations of the framework or test scenarios.

## 2. Local TLS and contract containers

```powershell
Set-Location local-services/scheduler-regression
docker pull gradle:8.4-jdk17
.\prepare-container-tests.ps1 -MutualTls
$here = $PWD.Path.Replace('\','/')
docker run --rm --env-file .runtime/local.env `
  --mount "type=bind,source=$here/.runtime/full-stack,target=/runtime" `
  --mount "type=bind,source=$here/generate-local-tls.sh,target=/generate.sh,readonly" `
  gradle:8.4-jdk17 sh /generate.sh
if ($LASTEXITCODE -ne 0) { throw 'Local TLS preparation failed' }
docker compose --project-name scheduler-regression-local --env-file .runtime/local.env `
  -f compose.local.yml -f compose.contracts.yml up -d --wait --wait-timeout 90
docker compose --project-name scheduler-regression-local --env-file .runtime/local.env `
  -f compose.local.yml -f compose.contracts.yml exec -T dependencies python -c `
  "import json,urllib.request; r=json.load(urllib.request.urlopen('http://scheduler:8080/actuator/health/readiness',timeout=10)); print(r); assert r['status']=='UP'"
```

Certificates are disposable, valid seven days, and generated in ignored .runtime.
For expiration, stop the local TLS proxy, retain the old TLS directory under a
different local name, generate fresh certificates and recreate the proxy. Do not
delete shared certificates or change corporate stores. Never ship local private
keys, local.env, test-work or runtime dependency bundles in a replacement patch.

## 3. Actual container execution

```powershell
$run = Get-Date -Format 'yyyyMMdd-HHmmss'
docker compose --project-name scheduler-regression-local --env-file .runtime/local.env `
  -f compose.local.yml -f compose.contracts.yml run --rm tests `
  gradle --offline --no-daemon --console=plain "-PrunId=$run" `
  -PtestTimezone=Europe/Moscow schedulerContainerRegression
```

To select transport smoke cases append `--tests '*SchedulerApiRegressionFlowTest.sch135'`.
SCH-134 exercises epoch round-trip; selecting it with Europe/Moscow reproduces
the test-side timestamp interpretation defect before the UTC fix.
Do not modify runtime properties while a test container is still running.

Test configuration is generated only under .runtime/full-stack/test-work.
Defaults enable fixture writes ONLY against the dedicated local database and its
paused workers. These properties must never replace corporate test.properties.

Results: .runtime/full-stack/results/<run>/allure-results, junit and test-report.
Keep each run separate. Allure can be generated using `allure generate <raw> -o <new-output>`.
An assertion failure leaves a nonzero Gradle exit; do not suppress it.

## 4. Dev-like schema constraint and negative TLS probes

The supplied archive creates nullable task.version. To check the dev constraint,
FIRST ensure the dedicated local database has no remaining tasks, then on that
container only apply:

```sql
ALTER TABLE scheduler.task ALTER COLUMN version SET DEFAULT 1;
ALTER TABLE scheduler.task ALTER COLUMN version SET NOT NULL;
```

Regenerate test properties with `prepare-container-tests.ps1 -MutualTls -SchemaProfile dev-v2`
and run again. This models the known nullable/default difference, NOT the entire
dev migration history, image or permission model. Restore DROP NOT NULL / DROP
DEFAULT only on the same disposable database if returning to the archive baseline.

Transport probes use SCH-135 separately: missing truststore path must fail setup;
the TLS proxy's Docker container name (not in the certificate SAN) must fail hostname
validation; the mTLS endpoint without a client certificate must reject the handshake.
Restore generated normal properties between probes. Intentional negative probe
failures must not be counted as successful business scenarios or merged into regression.

## 5. Stopping

```powershell
docker compose --project-name scheduler-regression-local --env-file .runtime/local.env `
  -f compose.local.yml -f compose.contracts.yml stop
```

This retains the local volume and evidence. No global Docker cleanup or down -v
is needed, and unrelated splitter containers must not be stopped or changed.
