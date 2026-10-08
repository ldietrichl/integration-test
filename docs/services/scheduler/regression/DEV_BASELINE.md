# Scheduler dev baseline received 2026-09-13

Reference snapshot:

```text
<workspace>\BACK\01_reference_sources\container_services\scheduler_service\dev_snapshot_2026-09-13_140519
```

Analysis and machine-readable contract inventory:

```text
<workspace>\BACK\05_outputs_results\2026-09-13_scheduler-dev-contract-analysis\ANALYSIS.md
<workspace>\BACK\05_outputs_results\2026-09-13_scheduler-dev-contract-analysis\contracts.json
```

Do not treat this snapshot as permission to connect tests to dev. The supplied SQL and manifests are reference data, not installation scripts.

## Preconditions that differ from the local archive-based run

- Dev task.version is NOT NULL DEFAULT 1. Existing legacy fixtures explicitly using null are incompatible with this schema. Keep them on an applicable legacy baseline; do not silently rewrite null to 1.
- Dev task_action.task_id is UNIQUE, so multiple action rows cannot share one task.
- Scheduler job flags shown in the ConfigMap are enabled. Fixture isolation cannot be claimed on that running profile.
- Deployed image is scheduler-service68, digest a30d7f32c5199dc46b8aea25ff2b64b7ecbb863d22d0f72e28eaeb526dee19a6. Its correspondence to source revision 073c1e7d873 is unknown.
- External secret configuration and global-config influence effective configuration. The received ConfigMap is incomplete as a runtime baseline.
- select.sql is a query script, not dictionary or Liquibase data.

## Contract sources now available

Producer and scheduler-consumer sources are preserved for dictionary, legacy user, v2 user and experiment. Use these to build explicit synthetic fixtures and dependency stubs, not as proof of dev deployment compatibility. The communication criteria/operators provider contract is still missing.

The archived scheduler calls the single experiment status endpoint. The provider's bulk endpoint is separate; never accept one as an alias for the other in a contract test.

No test code, live connections, fixture flags or service behavior were changed while importing this snapshot. No additional tests were run. Existing archive-based Allure results remain historical and must not be relabeled as dev results.
