# Scheduler regression: local container runbook

The service has now been built and started locally. See `LOCAL-CONTAINERS.md` for the actual Compose setup and commands, and `RESULTS-LOCAL.md` for the completed 15-scenario run. Earlier blocked-attempt records below are retained as history.

Latest execution attempt: see `RUN-2026-09-13.md` in this directory. Docker access was obtained with an approved elevated tool invocation; the initial access-denied status below describes the earlier attempt only.

## Recorded status: 2026-09-13

- Docker CLI is installed. The local Docker Engine could not be queried: `permission denied while trying to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`.
- No scheduler container was started and no live scheduler regression was executed in this session.
- Commands below are operator instructions, not a record of successful execution.
- The stand is prepared manually. No stand-controller API is required or assumed.
- Gradle 7.3.3 compilation was attempted separately. The full project compilation is currently blocked by unavailable optional Agent/Ragas/Spring AI dependencies. This does not establish that scheduler tests compile or pass.

## Source and image prerequisites

The supplied source archive is stored at:

```text
<workspace>\BACK\08_container_services\services_store\scheduler-service-develop-gecko@073c1e7d873.zip
```

Its extracted source is currently at:

```text
<workspace>\BACK\06_temp_work\scheduler-service-develop-gecko@073c1e7d873
```

The supplied Dockerfile uses a corporate Sber JDK 17 runtime image. Its build context must already contain the service JAR and the `secman` directory. A source ZIP alone is not a runnable image. Obtain the approved service image with its exact tag/digest, or build the JAR using the service project's documented build process and corporate dependency access. Gradle 8.4 applies to integration-test compilation, not necessarily to building this service.

The original entrypoint runs `/app/waiting.sh` before Java. Its `/app/wait.txt` names `/app/secrets/dboption.yml`. Mount the real, correctly formatted database configuration there. The waiting script only checks file existence, waits up to 60 seconds, and does not prove that configuration or database access is valid. Do not create an empty placeholder to bypass it.

If building an image from an already prepared build context:

```powershell
$source = '<workspace>\BACK\06_temp_work\scheduler-service-develop-gecko@073c1e7d873'
$jar = Read-Host 'Exact service JAR filename in the build context'
$image = Read-Host 'Local image name and explicit tag'
docker build --build-arg "JAR_FILE=$jar" --tag $image $source
if ($LASTEXITCODE -ne 0) { throw 'Image build failed; do not continue' }
```

Image creation was not executed locally. Registry access, a compatible JAR and the Dockerfile's corporate base image remain prerequisites.

## Manually prepare an isolated stand

Use a disposable database/schema and test accounts. Do not point the suite at production or a shared schema containing real tasks: scheduler jobs and cleanup cases can change or delete data.

Prepare these dependencies before starting the scheduler:

- PostgreSQL with the service-compatible schema, runtime account and migration account. Give migration privileges only to the migration account.
- Experiment, communication, user and dictionary services, or approved stand substitutes implementing the required contracts. Their presence is not supplied by this runbook.
- Service configuration matching the image version: bind address/port, database URL/schema, dependency endpoints, TLS material, scheduler flags, timeouts and database credential refresh settings/files.
- `dboption.yml` and any additional files required by that configuration. Container UID 1001 must be able to read mounted files.
- Separate users and splitting-point data required by the regression scenarios.

An endpoint inside the container cannot use `localhost` to reach a Windows-hosted dependency. With Docker Desktop, use the configured host gateway such as `host.docker.internal`, or use dependency container names on an explicitly prepared Docker network. Confirm routing in your environment.

Store secrets outside the repository and outside the replacement-file patch. Use a private working directory for the service environment file and mounted secret/config files. Do not commit passwords, tokens, private keys, or populated environment files.

The environment file consumed by `docker run --env-file` must contain actual `NAME=value` entries supported by this image. Docker does not expand `${...}` references in that file. Use the supplied service configuration as the authority; no unverified environment-variable template is provided here.

For REST/DB fixture cases, pause the background jobs before running tests. For worker, failure and timing cases, prepare the specific scenario instead. Do not interpret a test-side `jobs.paused=true` setting as a command that actually stops service jobs.

## Start the container from PowerShell

First open Docker Desktop, select Linux containers and restore authorized access to Docker Engine. Do not weaken Docker socket permissions to work around an access error.

```powershell
docker version
if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is unavailable' }

$image = Read-Host 'Approved scheduler image with exact tag or digest'
$envFile = (Resolve-Path -LiteralPath (Read-Host 'Absolute path to private service env file')).Path
$dbOptions = (Resolve-Path -LiteralPath (Read-Host 'Absolute path to prepared dboption.yml')).Path
$hostPort = [int](Read-Host 'Unused Windows host port')
$containerPort = [int](Read-Host 'HTTP port configured inside the service container')

docker run --detach --name scheduler-regression `
  --publish "127.0.0.1:${hostPort}:${containerPort}" `
  --env-file "$envFile" `
  --mount "type=bind,source=$dbOptions,target=/app/secrets/dboption.yml,readonly" `
  "$image"
if ($LASTEXITCODE -ne 0) { throw 'Container creation failed' }
```

Before execution, add any additional read-only configuration/certificate mounts and the intended Docker network required by your prepared stand. The command above is the minimum launch shape from the supplied Dockerfile, not a complete substitute for the image's configuration. It deliberately preserves the original entrypoint. If `scheduler-regression` already exists, inspect it and reuse or remove it deliberately; do not blindly delete an existing container.

## Readiness and diagnostics

```powershell
docker ps --filter 'name=^/scheduler-regression$'
docker logs --tail 200 scheduler-regression
$baseUri = "http://127.0.0.1:$hostPort"
Invoke-RestMethod "$baseUri/actuator/health/liveness"
Invoke-RestMethod "$baseUri/actuator/health/readiness"
```

Use the actual HTTPS scheme, management port and authentication if configured. HTTP 200 on liveness alone does not establish that migrations, dependency access and background jobs are ready. Check readiness, startup logs and the prepared database schema. Do not use certificate-validation bypasses as a permanent configuration.

## Configure integration-test

Project configuration entry points:

```text
src/test/resources/scheduler.properties
src/test/resources/test.properties
docs/services/scheduler/regression/README.md
```

Use the existing project environment/secure-property mechanism and the existing `explab` database client. Its connection must target the same isolated database as the scheduler. Set the scheduler base URI to the published local address, configure the required test users, and supply secrets through the project's secure configuration rather than source files.

Current regression tasks use the project's `dev`/`ift` environment and regression-profile conventions. Do not invent a `local` profile without integrating it into the project. Select the intended environment and profile using the existing project configuration before executing Gradle.

Enable fixture writes only after ensuring that the selected stand is isolated and jobs really are paused. Manually prepared cases require their own setup and fresh observations, not a REST controller. Reused observations from an earlier run are not evidence for the current run.

## Compile and run with Gradle 8.4

Use JDK 17 and an installed Gradle 8.4 distribution, or the project wrapper only after confirming that it resolves 8.4. Corporate repositories and project dependencies must be available. Local cached dependencies are not part of the replacement-file package.

```powershell
Set-Location '<project-root>'
$gradle = '.\gradlew.bat'
& $gradle --version
if ($LASTEXITCODE -ne 0) { throw 'Gradle is unavailable' }
# Confirm that the displayed version is 8.4 before proceeding.
& $gradle --no-daemon --console=plain testClasses auditReportingTags
if ($LASTEXITCODE -ne 0) { throw 'Compilation or tagging audit failed; do not run regression' }

# Run only after the isolated REST/DB stand has been prepared.
& $gradle --no-daemon --console=plain schedulerServiceRegression

# Run separately after preparing the required manual scenarios/evidence.
# & $gradle --no-daemon --console=plain schedulerManagedRegression
```

Compilation is not a live regression run. Do not report planned requirement coverage as executed/passing coverage. Cases requiring clock changes, network faults, pod/container stops, database rotation or retention fixtures need their own manual preparation and restoration. Do not launch the complete managed suite against an ordinary healthy stand.

## Preserve evidence and stop

Project regression artifacts are written under `build/regression-results/<environment>/<stage>/runs/<run-id>/`. Preserve the run directory and record the exact service image digest, source revision, Gradle/JDK versions, environment and scenario-specific manual actions. Do not run `clean` before collecting required results.

For local container logs, choose a private output directory outside the patch and repository:

```powershell
$evidence = Read-Host 'Absolute path to private evidence directory'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
docker logs --timestamps scheduler-regression 2>&1 |
  Set-Content -LiteralPath (Join-Path $evidence 'scheduler-container.log') -Encoding UTF8
docker inspect --format '{{.Image}}' scheduler-regression |
  Set-Content -LiteralPath (Join-Path $evidence 'scheduler-image-id.txt') -Encoding UTF8
docker stop scheduler-regression
```

Review and redact logs before sharing. Do not export full `docker inspect` output because it may contain environment secrets. Stopping the container retains it for diagnosis. If it is no longer needed, explicitly remove only this container with `docker rm scheduler-regression`; do not use volume deletion or system prune commands.

Restore manually changed clocks, network rules, credentials and job flags. Verify restoration with the stand owner. Remove test data only from the reserved scenario namespace or dispose of the dedicated test database through the agreed process. The instructions do not authorize deleting a shared database.

## Local execution ledger

| Action | Actual result |
| --- | --- |
| Inspect supplied Dockerfile/startup script | JDK 17 corporate base; UID 1001; `/app/secrets/dboption.yml` required by wait list |
| Locate Docker CLI | Installed at `C:\Program Files\Docker\Docker\resources\bin\docker.exe` |
| Query Docker Engine version | Failed: access denied to Docker Desktop Linux Engine pipe |
| Build/pull/start scheduler image | Not executed |
| Run scheduler regression against containers | Not executed |
| Full integration-test compilation, Gradle 7.3.3 | Blocked by missing optional Agent/Ragas/Spring AI dependencies |

Append actual commands, timestamps and outcomes when local execution becomes possible. Never replace a failed/not-executed entry with a success based only on written instructions.
