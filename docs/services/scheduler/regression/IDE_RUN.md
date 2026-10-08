# Scheduler: IDEA and Gradle execution

## IDEA package / class / method

Right-click `src/test/java/ru/sber/qa/scheduler/regression` and select Run Tests,
or use the run icon on a test class/method. Both direct JUnit and Gradle-delegated
execution use the existing framework environment configuration and flow steps.
Use the integration-test module classpath and the project root as working directory.
For direct JUnit, enable annotation processing/build prerequisites as in the other
project tests. Reload the Gradle project after installing replacement files.

Select `env=dev` or `env=ift` in the existing `src/test/resources/test.properties`.
Keep database credentials in the existing database.properties / secure mechanism;
the database client is still the framework's explab service.

The package includes a read-only DB preflight and SCH-135 health/metrics.
With fixture switches disabled, write scenarios are DISABLED before fixture setup,
with a reason, instead of failing with repeated permission exceptions.
They are not successful tests and do not establish regression coverage.
Once switches are enabled, the existing fixture permission checks still apply.
Schema-incompatible legacy tests remain skipped on dev-v2.

Only after an operator has actually isolated test data and paused scheduler jobs:

```properties
scheduler.dev.fixtures.enabled=true
scheduler.dev.fixtures.isolated=true
scheduler.dev.jobs.paused=true
```

Use ift instead of dev for that environment. These flags do NOT prepare the stand.
Prepared-stand scenarios are disabled by default even with direct JUnit execution.
For an intentional direct JUnit run, after preparing fresh observations, set
`scheduler.dev.manual.enabled=true` in test.properties, or provide the JVM option
`-DincludeManualTests=true`. For the dedicated Gradle managed task use the explicit
project option shown below. Its value takes precedence over resource configuration.
When Gradle delegates package execution, the project's additional report eligibility
rules still apply; use schedulerManagedRegression for intentional managed runs.

## Gradle panel

Tasks > regression > schedulerRegression runs the automatic API suite and DB
preflight checks. The preflight check inside this suite is not an ordering barrier:
for a staged first installation, run schedulerPreflight separately first.

```powershell
# Use your installed Gradle 8.4 executable.
gradle schedulerPreflight --console=plain
gradle schedulerRegression --console=plain
# Existing API-only task is preserved:
gradle schedulerServiceRegression --console=plain
# Separate operator-prepared run, not included in schedulerRegression:
gradle schedulerManagedRegression -PincludeManualTests=true --console=plain
```

Dedicated schedulerRegression artifacts are written by the existing pipeline to
`build/regression-results/<env>/scheduler-regression/runs/<run-id>/`.
IDE direct JUnit and ordinary Gradle test use their existing report locations,
not the dedicated task's directory.

## Existing TLS blocker

The previous SCH-135 trustAnchors error is not fixed by changing the launch mode.
Configure the corporate trusted CA store for the project's REST client / test JVM.
Do not disable certificate validation or assume a bearer token fixes a TLS handshake.

This revision has not been compiled, run or verified in IDEA locally.
