# Scheduler v4: measured local validation, 2026-09-13

## Delivered fixes

- Retains v3 IDEA/package execution, Gradle scheduler tasks and preflight discovery fix.
- Includes v3.2 strict truststore/mTLS integration with existing project secure properties.
- Fixes PostgreSQL UTC timestamp WITHOUT time zone conversion in SchedulerDbSteps:
  before the fix, SCH-134 in Europe/Moscow read 1789989200123 instead of 1790000000123.
  After the fix the same scenario passes. Fixture SQL explicitly writes UTC wall time,
  independently of the database session and test JVM time zones.
- Failed HTTP checks now show expected and actual status while retaining the project's
  matcher and its original failure cause; no body or secret is added to the message.
- Invalid-create cases check response status and absence of writes independently.
- SCH-021 seeds equal primary-sort values and asserts secondary createdAt ordering.
- SCH-093 checks malformed JSON on all three routes even if the first assertion fails.

## Actual results

| Run | Passed | Failed | Skipped | Total |
|---|---:|---:|---:|---:|
| Initial archive schema, HTTPS | 35 | 35 | 0 | 70 |
| Before UTC fix, SCH-134 + SCH-135, mTLS / Europe/Moscow | 1 | 1 | 0 | 2 |
| Final archive schema, mTLS / Europe/Moscow | 35 | 35 | 0 | 70 |
| Final dev-like version constraint, mTLS / Europe/Moscow | 25 | 29 | 16 | 70 |

Each 70-case run includes 69 API scenarios and ONE supporting preflight test.
The dev-like run only reproduces task.version NOT NULL DEFAULT 1. It is not the
corporate image, full migration history or real provider infrastructure. The 16
legacy-fixture skips are expected for that schema and are not passed coverage.

SCH-134 and SCH-135 pass in the final runs. Independent negative transport probes
reject a missing truststore, a hostname absent from SAN and a missing required
client certificate. Those deliberately failing SCH-135 invocations are transport
checks, NOT extra business tests and are kept out of the main regression report.

The final archive raw Allure contains 35 passed / 35 failed and no orphan container
children. JUnit agrees with those counts. Do not merge repeated runs as unique coverage.

## Remaining failures are not hidden

The test harness runs, but the regression is NOT green. The final archive run retains
35 failed scenario expectations; this does NOT mean 35 independently confirmed
product defects. They include documentation questions and observed implementation
differences. No expectation was changed to accept HTTP 401 instead of 400.

| Cases | Observed difference / interpretation |
|---|---|
| SCH-002,005 | v1 exposes tasks with version=1; missing version restriction in service reads |
| SCH-003,006,007 | Documented GET/array forms differ from implemented POST/scalar forms; response 401 also masks error dispatch |
| SCH-018 | v2 search does not filter the seeded rows |
| SCH-019,021 | ASC/secondary ordering fails; source compares distinct SortDirection enum types |
| SCH-022,023,027 | Missing or malformed pagination results in 401; null page/size errors are logged |
| SCH-030 | Registry returns splitting-point code as name, not the supplied dictionary name |
| SCH-033,035 | Plan names differ from supplied dev dictionary and source enum; contract owner must reconcile terminology |
| SCH-036,038 | Missing splittingPointCode/createdBy is accepted and creates data; createdBy also has authenticated-user derivation, requiring contract clarification |
| SCH-037,039-044,053 | Invalid inputs trigger conversion/null/DB errors rather than the expected validation response |
| SCH-046 | Two actions create two tasks; documentation expectation conflicts with the one-action-per-task schema/model |
| SCH-048 | Response DTO lacks the documented version field |
| SCH-055,056 | Deletion considers only the first 2000 actions and leaves a planned task |
| SCH-057 | No matching v2 task produces 200 instead of the plan's 400; clarify no-op deletion contract |
| SCH-093 | All three malformed-JSON routes produce 401 instead of 400 with UUID error payload |
| SCH-126,127,129 | Invalid filters produce errors masked as 401 |
| SCH-128 | Empty filter values produce 200 instead of the plan's 400 |
| SCH-131 | Proposed, unconfirmed fallback expects last nonempty page; implementation returns page 0; do not call this a confirmed requirement defect |

Known migration/profile differences are not fixed by editing the real service or
silently replacing legacy NULL versions with 1. The 68 managed/fault scenarios remain
outside this autonomous run. 85% requirement coverage has NOT been established.

## Reproducibility and limitations

Real components: the supplied scheduler archive JAR, PostgreSQL 16, HTTPS/mTLS proxy
and a Gradle 7.3.3/JDK 17 test container. Dictionary/user/experiment/communication
are user-approved source-derived contract fixtures, not production services.
The proxy forwards statuses/bodies without weakening business assertions.
Worker and cleanup jobs are disabled; this is not fault/restart/rotation validation.

Service JAR SHA256:
56CDCC6F6ECEA71E2565A5557C026E12D3BDA164B8272F7D0E82462E2290A797

Host compilation uses the real project's Gradle 7.3.3 build and exports 184 runtime
JARs. JUnit runs inside the Gradle 7.3.3 container using those compiled project classes.
Offline compilation excludes three unrelated optional sources whose dependencies
are unavailable locally, as documented in the local runbook. That exclusion and the
local dependency mirror are NOT shipped in the corporate replacement patch.
Full unmodified corporate compilation remains a required installation check.

Local startup instructions are saved separately in
local-services/scheduler-regression/FULL-STACK-RUNBOOK.md. Runtime passwords,
private keys, containers, mirrors and local endpoints are excluded from the patch.
Corporate dev/ift uses the existing test.properties, database.properties, framework
explab DB client, REST configuration and secure overrides.

Configure the real corporate truststore before rerunning SCH-135. Enable fixture
permissions only after operator preparation; this patch does not stop dev workers.
