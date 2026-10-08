# Scheduler regression: Pilot-style flows and direct HTTP

## Implementation

The existing SchedulerApiRegressionFlowTest, SchedulerManagedRegressionFlowTest and
SchedulerPreflightFlowTest classes now extend AbstractSchedulerFlowTest, like the Pilot
tests extend AbstractPilotFlowTest.

Each scenario has its own getFlowWithDbRest().step(...).run() chain. SCH-135 uses the
REST-only flow. REST calls are visible through flow.restCustomSteps().schedulerSteps();
database access stays on flow.dbCustomSteps().schedulerSteps(), using the existing
framework explab client. There are no new JDBC connections or standalone HTTP clients.

- request.scheduler.SchedulerTestDataFactory owns request maps, unique names and object IDs.
- util.scheduler.SchedulerAssertions contains response and persistence assertions.
- util.scheduler.SchedulerManagedAssertions provides named evidence oracles, not an ID switch.
- util.scheduler.SchedulerScenarioCatalog preserves requirement and Allure identities.
- AbstractSchedulerFlowTest registers owned fixtures before writes and removes them in
  @AfterEach, including after failed tests. Cleanup failures are reported, not silently ignored.
- The four obsolete test-package dispatcher/context classes must be removed when installing.

Scenario method names sch001 ... sch137, display names, CriticalRegression/Regression tags,
the resource lock and sequential execution are retained. ManualTest remains on managed
scenarios because those still require operator-prepared state and fresh observations.
No arbitrary Allure TestOps IDs have been assigned.

## Direct service endpoint, no scheduler mTLS

For a corporate workstation outside the cluster, keep an authenticated port-forward open:

```powershell
oc -n ci07963639-dev-terra000003-abtm-back port-forward --address 127.0.0.1 service/scheduler-service 28089:8080
```

For IFT use its actual namespace, not the DEV namespace:

```powershell
oc -n <actual-ift-namespace> port-forward --address 127.0.0.1 service/scheduler-service 28090:8080
```

Select exactly one environment in src/test/resources/test.properties:

```properties
env=dev
rest.dev.scheduler.base-uri=http://127.0.0.1:28089
rest.ift.scheduler.base-uri=http://127.0.0.1:28090
scheduler.dev.mtls.enabled=false
scheduler.ift.mtls.enabled=false
```

These are loopback tunnel addresses, not service DNS names for an in-cluster test runner.
An in-cluster runner can instead use an authorized, reachable scheduler Service DNS name
on port 8080. Ordinary Service traffic may still pass through the mesh; direct access
does not change cluster security policies.

Remove obsolete scheduler.base-uri / scheduler.<env>.base-uri overrides if present:
those have higher priority than rest.<env>.scheduler.base-uri. Do not change the common
gateway URI or point other clients at the scheduler tunnel. Keep the scheduler URI
explicit because the existing shared resolver still has a gateway fallback.

The service paths remain /api/v1/schedule/... and /api/v2/schedule/....
Do not append these paths to base-uri itself. The supplied service exposes HTTP 8080.
The HTTP branch of SchedulerRestConfiguration does not load SchedulerTlsConfiguration,
so scheduler key/trust stores are not needed. Other services' TLS settings are unchanged.
Do not delete shared keystore.pass, truststore.pass or certificate files used elsewhere.

Port 18089 belongs to the local Docker scheduler. Do NOT use it with the corporate DEV DB.
The corporate port-forward must target the same stand as db.<env>.explab.*.
The tunnel uses Kubernetes API authentication; this does not disable cluster authorization.

SCH-059, SCH-060 and SCH-061 are @Disabled for direct HTTP regression. They retain their
documentation IDs, but are not executed and do not count as covered/passed TLS requirements.
Ingress, proxy and mTLS behavior is out of scope of this direct backend run.

## Prerequisites and evidence

The API suite still has 69 scenarios. Managed scenarios retain 68 catalog identities,
of which 3 TLS scenarios are disabled; the remaining 65 still require prepared-stand
evidence. Preflight is an additional infrastructure check, not another requirement.

The dev-v2 schema still cannot execute the 16 nullable-version legacy scenarios:
SCH-001, 003, 004, 005, 006, 007, 057, 095, 105, 106, 107, 108, 109, 110, 131, 132.
A style refactor does not remove this incompatibility or demonstrate 85% coverage.

Keep fixtures.enabled, fixtures.isolated and jobs.paused false until an operator has
actually prepared an approved isolated stand. Do not pause shared DEV/IFT jobs as part
of installing this patch. Preserve real corporate user IDs, secure DB bindings and
expected dictionary/user data. Local stub user IDs are not corporate fixture identities.

Managed setup.json must match caseId, environment, buildId, runId and the exact resolved
schedulerBaseUri, including the loopback tunnel URI if used. observations.json must be
fresh for the current run. Java-recorded REST responses remain authoritative.
Database-rotation/migration cases retain their existing exception from status reconciliation.
Performance cases still abort without an agreed SLA; they are not converted to PASS.

## Commands to run after installation

Use the project's Gradle 8.4 wrapper and configured JDK. The rewrite itself has NOT
been compiled or executed.

```powershell
.\gradlew.bat testClasses -x testOpsUpload
.\gradlew.bat schedulerPreflight -x testOpsUpload
.\gradlew.bat schedulerServiceRegression -x testOpsUpload
.\gradlew.bat schedulerRegression -x testOpsUpload
.\gradlew.bat schedulerManagedRegression -PincludeManualTests=true -x testOpsUpload
```

schedulerServiceRegression selects API/DB cases; schedulerRegression adds preflight.
schedulerManagedRegression is a separate, operator-prepared stage.
Run a stage only after its prerequisites have been met, not all commands blindly.
The existing task names, IDE package-run entry points and report destinations are unchanged.
Do not clear the build directory if existing run evidence must be preserved.
