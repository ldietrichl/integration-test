# Scheduler corporate overlay v2

## Framework integration

Environment is selected by `env=dev` or `env=ift` in the existing
`src/test/resources/test.properties`. Database access stays in the existing
`DatabaseService` / `explab` client via `getFlowWithDbRest()` and
`f.dbCustomSteps().schedulerSteps()`. No DriverManager, separate JDBC pool,
container connection or credentials are supplied by this patch.

Keep the existing `db.<env>.explab.*` settings in `database.properties`,
including the project's secure placeholders. REST uses the project RestClient.
Do not replace corporate connection or secret files with local configurations.

## Configuration to merge into test.properties

```properties
# Select dev OR ift using the existing env property.
env=dev
scheduler.dev.schema.profile=dev-v2
scheduler.dev.fixtures.enabled=false
scheduler.dev.fixtures.isolated=false
scheduler.dev.jobs.paused=false
# Configure existing scheduler REST URI, authorized users and secure token
# using the project's established keys. Never put a token in this document.
```

Use `scheduler.ift.*` for ift. `dev-v2` requires a NOT NULL version column;
`legacy-null` requires a nullable version column. The check does not alter DDL.
The snapshot's unique task_action constraint is attached to Allure, not removed:
multi-action requirements remain assertions and can expose service/schema defects.

`schedulerPreflight` only reads schema, dictionary/migration counts and constraints.
Counts are an inventory, NOT full validation of reference values or API contracts.
SCH-135 no longer installs or cleans up DB fixtures. Framework initialization
can still require the usual DB/REST configuration even for that health scenario.

## Write-test safety

Only enable all three fixture switches after an operator has independently paused
scheduler processing/cleanup jobs and reserved an isolated QA namespace. A property
is an acknowledgement, not a command to stop jobs and not proof that they stopped.
Do not set jobs.paused=true on a running shared dev merely to bypass the guard.
Tests create synthetic task/action objects; they must not reach real job workers.
Cleanup remains registered with the project's FixtureService.

On dev-v2, legacy-fixture scenarios SCH-001,003,004,005,006,007,057,095,
105-110,131,132 are aborted before fixture writes, not reported as passing.
They need a separate compatible legacy baseline; NULL is never replaced by 1.

## Prepared-stand tests and coverage

The 68 evidence-dependent scenarios still use the prepared-stand adapter and
fresh observations. They are not converted into autonomous worker/fault tests by
this overlay. The class retains ManualTest to prevent unattended evidence runs.
Explicit `-PincludeManualTests=true` now reaches both discovery and execution for
`schedulerManagedRegression`; it does not enable manual tests in other stages.
Without opt-in that task may have no matching tests, by design.

Requirement coverage must exclude preflight checks and must not count skipped
or unsupported scenarios as passed. This patch does not establish 85% coverage.
No expected HTTP errors were weakened to accommodate known service defects.

## Validation status

This revision has not been compiled or executed. Run with corporate Gradle 8.4
and available Nexus dependencies. Previous local results do not validate v2.
