# Scheduler regression

137 scenario IDs from the scheduler regression plan are registered in the integration-test project:

- 69 REST/PostgreSQL tests in `SchedulerApiRegressionFlowTest`.
- 68 stand-managed tests in `SchedulerManagedRegressionFlowTest`.
- Reusable transport steps: `steps.rest.scheduler.SchedulerSteps`, exposed through `RestCustomSteps.schedulerSteps()`.
- Reusable PostgreSQL observations/fixtures: `steps.db.scheduler.SchedulerDbSteps`, exposed through `DbCustomSteps.schedulerSteps()`.
- Both suites use the project's `Flows`, Platform V AT `RestService`/`DatabaseService`, Perfeccionista environment, fixture lifecycle and Allure invocation services.

The managed suite requires a real stand controller implementing [CONTROL_PROTOCOL.md](CONTROL_PROTOCOL.md). **No scheduler controller implementation or deployment is included in this patch.** The client protocol extends the project's existing capabilities/session/observations approach used by EXPLAB-2972, but is not automatically supported by that controller. Without the adapter these 68 scenarios cannot run. They are marked `@ManualTest` and are not reported as successful when prerequisites are absent.

## Configuration

`env` in `src/test/resources/test.properties` remains the environment selector. Scheduler defaults are in `src/test/resources/scheduler.properties`. Values in test.properties take precedence. Existing secure resolution handles `${SECURE_...}` references; do not commit tokens/passwords.

Example additions to test.properties (replace placeholders locally):

```properties
rest.dev.scheduler.base-uri=https://scheduler.internal.example
scheduler.dev.user-id=101
scheduler.dev.other-user-id=102
scheduler.dev.token=${SECURE_SCHEDULER_TOKEN}
scheduler.dev.fixtures.enabled=true
scheduler.dev.jobs.paused=true
scheduler.dev.splitting-point=MAPPER
scheduler.dev.splitting-point-name=Mapper
scheduler.dev.expected-creator.employee-id=QA101
scheduler.dev.expected-creator.email=qa101@example.invalid
scheduler.dev.mtls.enabled=true
scheduler.dev.keystore=src/test/resources/keystore.p12
scheduler.dev.keystore.password=${SECURE_KEYSTORE_PASSWORD}
```

Use Java properties Unicode escapes for non-ASCII values when editing a file read with `Properties.load(InputStream)`. The example names above are placeholders, not stand requirements.

The database client is the existing `explab` configuration in `database.properties`, pointing to a PostgreSQL database with the scheduler schema. The account needs SELECT and fixture INSERT/DELETE privileges on scheduler.task and scheduler.task_action. No new JDBC implementation is introduced.

**Pause actual v1 and v2 polling/cleanup jobs before the API/DB suite.** The jobs.paused setting is an operator confirmation, not a command that pauses the service. Fixtures can contain due dates, legacy versions and foreign ownership; use an isolated QA database and prepared QA identities, never production.

Fixtures use a UUID marker and a previously absent pair of random object IDs. Cleanup is registered through FixtureService and executes on failure. Cleanup removes only the reserved namespace. A failed preflight never authorizes cleanup. No global truncate/delete is used. A process killed before teardown requires manual cleanup of the recorded `SCHIT...` namespace after inspection.

## Run

JDK 17 and Gradle 8.4 are required by the existing project.

```powershell
.\gradlew.bat --version
.\gradlew.bat testClasses auditReportingTags
.\gradlew.bat schedulerServiceRegression
.\gradlew.bat schedulerManagedRegression -PincludeManualTests=true
```

The dedicated Gradle tasks use the existing `configureRebuiltRegression` pipeline, eligibility scanner, reporting rules and separate Allure result directories. That pipeline currently supports dev/ift. For other configured environments use the project's direct test/IDE workflow after verifying that the stand profile is supported.

```powershell
.\gradlew.bat test --tests "ru.sber.qa.scheduler.regression.SchedulerApiRegressionFlowTest.sch045"
```

The new tasks are not added to `regressionAll` or automatic TestOps upload. This avoids silently starting infrastructure scenarios or publishing results. Use the generated per-stage Allure results for the project's established review/upload procedure.

## TestOps and traceability

- Canonical service tag: `scheduler-service` for package `ru.sber.qa.scheduler` in functional and bypass listeners.
- `@Regression` adds regress; P0 cases use `@CriticalRegression`; managed class uses `@ManualTest`.
- No arbitrary `@Tag`, fabricated EXPLAB number, TestOps ID or uploaded PASS result.
- Each SCH ID has its own JUnit method, display name, stable Allure testCaseId/historyId and requirement label.
- Original scenario descriptions and requirement/source references are in `src/test/resources/scheduler/scenarios.json`.
- D19/D23/D24/D25 are still unresolved and have no tests in the plan. No execution coverage is inferred from the number of test methods.

## Assertions and limits

Assertions target the supplied documentation, including expected failures in the archive: one task with multiple actions, correct ASC sort, version isolation, required-field validation, bulk execution/requeue, cleanup fields and inclusive clock boundaries. Code-derived expectations are identified by C-requirements in the catalog.

The draft v2 specification is marked Development. Confirm its applicability to the deployed build before classifying failures as release defects. Ambiguous decisions remain: actions response shape, upsert/id, creator precedence, epoch units, search fields and exact monitoring event names.

Managed tests consume raw task snapshots, HTTP journals, job intervals, TLS handshakes and measurements. Expected statuses and thresholds are asserted in Java, not accepted from controller PASS/FAIL fields. Final task statuses are independently reconciled through the project's DB client where the current schema is available. The stand adapter is responsible for accurate intermediate captures and restoration; the protocol documents that dependency.

Performance SCH-102/SCH-104 collect and validate measurements but abort without a performance verdict because no SLA was supplied. SCH-101/SCH-098 use proposed determinism/terminal-status invariants that need product agreement. Running the suite does not measure JaCoCo line/branch coverage.

Some plan variants require separate stand runs: timezone profiles for SCH-134, downstream fault variants, server-version/profile changes and mTLS identities. A single method invocation is not proof that all configurations were exercised. Compilation is not a service regression run.
