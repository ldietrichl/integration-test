# Scheduler corporate infrastructure diagnostics v4

## Scope and validation status

Apply on top of the installed v3 infrastructure/logs patch.
One test class now contains 14 supporting, read-only hypotheses.
The v3 corporate run compiled on Gradle 8.4 and reported 7 passed, 2 failed,
3 native TLS failures and 1 skipped. Its oc log attachments worked.
The v4 sources have NOT been compiled, executed or post-edit reviewed during preparation.
Use the project Gradle 8.4 wrapper and JDK 17 for the next corporate run.

This patch corrects the V1/V2 metadata assumption rather than accepting the 401:
- SCH-INF-004 explicitly remains the V1 task-history dictionary contract.
- SCH-INF-006 reads the V2 AUTOSTART_TASK_LIST expression dictionary and tests a supported equal filter.
- SCH-INF-013 checks an advertised mapped numeric/date sort field with actual varied baseline values.
- New SCH-INF-014 compares API taskNumber to persisted task_number through the existing explab DB client.
- SCH-INF-007 retains strict OpenAPI string/date-time checks.
- SCH-INF-002/003/012 remain explicit Fabric8 checks; no TLS trust-store change is included.

No pod mutation, service deployment, DB fixture creation, direct JDBC/HTTP alternative or TestOps upload is added.
Shared settings, kubeconfig, certificate stores and Gradle task definitions are not replaced.
Existing business-regression classes do not automatically acquire these diagnostics.

## Dependency routing without ingress

SchedulerInfrastructureFlow reuses the existing DictionariesV1Steps operation and
ExpressionParameterDictReqDto. Despite the step class's historical name, this request is:

    POST /api/v2/dictionaries/expression-parameter-dict
    {"formCode":"AUTOSTART_TASK_LIST"}

The response uses paramCode, dataType, filterFlag, orderFlag and object-valued validOperators.
A present formCode must match AUTOSTART_TASK_LIST. Unknown mappings are not guessed.
V1 GET /api/v1/schedule/dictionary/filters is NOT used to authorize V2 field choices.

The registered KubernetesTunnelService owns a second explicit oc loopback tunnel
for the dictionary dependency. It uses the same selected DEV/IFT context and API server;
the namespace defaults to the scheduler namespace. No gateway/ingress URI fallback is used.
Dictionary namespace/service/service-port can be overridden for the actual dependency.

The dictionary request still uses the project's RestClient, DTO and existing REST step.
The new per-instance loopback route clears inherited scheduler/gateway Authorization,
Cookie and preconfigured HTTP authentication, rather than leaking them to another backend.
OpenShift authentication remains only in the existing kubeconfig.

The established one-argument DictionariesV1Steps constructor is unchanged in behavior.
No other dictionary tests are silently rerouted. Each owned tunnel is closed, including
on a failed assertion/assumption. Dictionary log selection never replaces scheduler log selection.
The dictionary may be load-balanced independently from the scheduler's own Feign call;
configuration and data must identify the same intended dependency, not an unrelated test service.

## Common DEV/IFT settings

Retain the existing values in src/test/resources/test.properties:

    kubernetes.transport=oc
    kubernetes.logs.enabled=true
    kubernetes.logs.container=scheduler-service
    kubernetes.tls.diagnostics.enabled=true

No new mandatory property is needed when the real dependency is dictionary-service:8080
in the scheduler namespace. These are configurable defaults, not a discovery claim about DEV.

Optional common overrides:

    kubernetes.dictionary.service=dictionary-service
    kubernetes.dictionary.service-port=8080

If the scheduler uses another dictionary namespace, set kubernetes.dictionary.namespace.
Environment-specific kubernetes.dev.dictionary.* or kubernetes.ift.dictionary.* overrides
the corresponding common setting. The exact context/API is never changed for the dependency.
Do not insert an IFT context or namespace by copying DEV identifiers.

Both dictionary and scheduler listeners use owned loopback addresses; the dictionary port is automatic.
There is no need to start a second manual port-forward or set a static dictionary REST URI.
When the selected dependency has other service/port values, configure its actual values first.
The scheduler's services.dictionary.url is the source of truth for which dependency to target.
No corporate CA, token or private key is included in the patch.

Service/pod discovery and create pods/portforward must be allowed for the dictionary namespace too.
get pods/log remains needed for scheduler log capture. The dictionary tunnel uses oc explicitly;
the scheduler transport remains configurable. No native-to-CLI fallback is introduced.

Existing log limits, startup/request deadlines, kubeconfig and oc executable settings remain.
The Gradle task enables this suite in its own JVM. Native IDEA JUnit Run still needs
scheduler.infrastructure.enabled=true. No fixture isolation/jobs-paused flags should be falsified.

## Scenario matrix

| Scenario | Transport | Assertion |
|---|---|---|
| 001 | Configuration/JVM | Explicit DEV/IFT context and TLS policy, not proof of a handshake |
| 002 | Fabric8 | Service discovery, Ready pod, reused framework client |
| 003 | Fabric8 + project REST | Scheduler and service DB health |
| 004 | Configured scheduler tunnel | V1 history dictionary shape/operator contract |
| 005 | Configured scheduler tunnel | Bounded V2 registry page |
| 006 | Scheduler tunnel + dictionary oc | V2-advertised equal filter over a mapped scalar |
| 007 | Configured scheduler tunnel | Strict documented string/date-time contract |
| 008 | Configured scheduler tunnel | Prometheus exposition |
| 009 | Framework explab DB | SELECT and scheduler baseline tables |
| 010 | Explicit scheduler oc | Independent HTTP/DB health control |
| 011 | Local sockets | Occupied listener is not stopped/reused |
| 012 | Fabric8 | Native session reuse/close/reopen |
| 013 | Scheduler tunnel + dictionary oc | V2-advertised ASC/DESC numeric/date ordering |
| 014 | Scheduler tunnel + framework DB | API taskNumber equals persisted nullable int64 |

## Read-only oracle boundaries

The metadata helper uses a finite, source-backed mapping:
id -> /id; createdBy -> /createdBy; updatedBy -> /updatedBy;
splittingPoint -> /splittingPoint/code; status -> /status/code;
createdAt -> /createdAt; updatedAt -> /updatedAt; scheduleDatetime -> /planDt.

006 chooses an advertised equal filter with compatible numeric, string or enum metadata
and a non-null observed value. Date filter inputs are excluded because the timestamp units
must not be guessed. Every returned row must match; taskNumber is not used as a proxy for id.
An unavailable mapping/data prerequisite is explicitly skipped with capability evidence.
An empty filtered result after a non-empty baseline fails for investigation.

013 selects a numeric/date orderFlag field with at least two distinct non-null values on
one bounded baseline page. No textual DB collation is guessed. Both ASC and DESC pages
must be monotonic and their first values must demonstrate different extrema.
A baseline with no suitable field/variation, or null-valued extrema, is an explicit skip.
The finite mapping is not a claim to cover every possible V2 field or null-order case.

Numeric date values are compared as numbers without guessing epoch units; text dates
are compared as offset-bearing ISO instants. Mixed date representations fail.
This ordering check does not weaken 007: the numeric-vs-OpenAPI mismatch stays visible.

014 queries only the ids returned in one V2 page, up to 20 ids, using SELECT on
scheduler.task with version=1 through SchedulerDbSteps and the registered explab connection.
It checks nullable int64 equality. API/DB missing or mismatched values fail.
If all persisted and returned values are zero, the constant-zero defect cannot be
distinguished, so the scenario skips instead of claiming converter coverage.

The source archive's TaskContentConverter assigns a constant taskNumber=0.
This patch does not modify the service or manufacture nonzero data. A nonzero/null DB
witness can therefore expose a genuine mapping disagreement in 014.
Exact deployed build identity and the authoritative service contract must still be considered.

The service and framework DB must refer to the same DEV/IFT stand.
These requests are not a cross-service transactional snapshot: concurrent edits/deletions
can change the data between API pages or between API and DB. Investigate such changes
rather than replaying business actions or weakening assertions.

Framework HTTP/DB evidence can include corporate record values. Keep the resulting
Allure artifacts inside the authorized environment and inspect before sharing.

## Service logs and Allure

A BeforeEach hook records the scenario id and UTC start time. It does not call Allure test metadata APIs.
AfterEach requests a bounded snapshot for the scenario window and attaches it BEFORE framework cleanup.
This covers passing, failed and assumption-aborted scenarios that selected a scheduler pod.
A disabled test, JVM crash or failure before Environment creation cannot promise a log attachment.

The attachments appear under the scenario's teardown/after fixture:
- Scheduler service log capture status: source pod/UID/container, UTC window, limits and outcome.
- Scheduler service log (sanitized, scenario time window): captured text, when available.
- Java TLS trust-source diagnostics (no credentials): in configuration/native diagnostic steps.

When logging is enabled, the CLI path first reads the selected Service and matching Ready pods through oc.
It resolves the service targetPort and pins port-forward to one explicit pod.
The log collector uses that same pod UID/container; it does NOT call logs service/... and risk another replica.
With logging disabled, the proven v2 service/ port-forward behavior is retained.

If optional source discovery fails, the CLI test retains the original service/ forwarding path and
attaches TARGET_DISCOVERY_FAILED metadata, without pretending another pod's log belongs to the scenario.
If native TLS fails before pod selection, NO_TARGET_NO_SERVICE_REQUEST is attached instead of unrelated logs.
No pod source is guessed for a DB-only or local-socket-only check.

The snapshot uses:

    oc ... logs pod/<selected-pod> -c scheduler-service \
      --since-time=<UTC-start-minus-skew> --timestamps=true \
      --tail=500 --limit-bytes=131072

The client also filters returned timestamps through scenario-end-plus-skew.
There is no unbounded --follow process. Log capture has its own deadline, output cap and bounded process cleanup.
Kubernetes get/logs commands use the same kubeconfig, explicit context, namespace and verified TLS policy.
No OAuth token is added to arguments, logs or project resources.

Window logs are context, NOT guaranteed per-request correlation:
- Other traffic against the same shared pod can appear in the window.
- Clock skew beyond the configured allowance can hide relevant lines.
- Buffered messages arriving after the snapshot may be absent.
- Tail/byte limits and log rotation can make evidence partial.
- Container restart counts are reported; previous-container logs are not automatically collected.
- A changed pod UID is rejected rather than attributed to the scenario.

### Permissions and failures

In addition to existing Service/pod/port-forward access, log capture requires get on pods/log.
A corporate operator may inspect authorization with:

    oc.exe auth can-i get pods/log -n <authorized-namespace>

This is a prerequisite check, not a request to grant wider permissions or disable RBAC.
A Forbidden/timeout/log-collection error is attached as collection status and does not replace
the original pass/fail/skip result. Existing cleanup exceptions are still visible.
An empty window is marked EMPTY_WINDOW, not fabricated as proof that the service had no issues.

### Redaction and confidentiality

Raw get pod/service JSON remains in bounded process memory and is never attached.
Credential-related lines and request/response payload lines are suppressed.
PEM blocks, standalone encoded data, known token formats, email addresses and common identity fields
are suppressed or masked. Query-string values and URL userinfo are removed from retained access summaries.

This is conservative filtering, NOT a guarantee of anonymizing arbitrary application prose.
Treat Allure results as corporate data and inspect them before external sharing.
The snapshot cannot simultaneously promise a complete unmodified forensic log and removal of all secrets.
Do not export secure.local*, kubeconfig, PKCS12 files or full project resources.

## JVM/CA diagnostics

Configuration/native steps attach:
- Actual Java version, vendor, java.home and installed security-provider names.
- Explicit javax.net.ssl.trustStore/type and file existence/readability.
- Standard JSSE jssecacerts/cacerts candidate path.
- Fabric8 CA-data presence, CA-file/trustStore-file metadata and public CA certificate count where readable.
- Default TrustManagerFactory provider and X509 accepted-issuer count, or a safe error type.

Where available, the native client configuration is captured with KubernetesClient.getConfiguration.
Before a client exists, the attachment is explicitly labelled as parsed kubeconfig, not effective runtime.
Optional Fabric8 getters are inspected reflectively for compatibility; unavailable values do not crash the test.

The default JVM accepted-issuer count is NOT necessarily the effective Fabric8 trust set.
Candidate file paths do not prove provider selection. Explicit Fabric8 truststore entries are not
enumerated using a guessed password. No passphrase, private client key, raw CA bytes or full Config
object is attached, and no certificate is installed.

The safe native cause classifier remains:
JAVA_TRUST_ANCHORS_EMPTY only for a real corresponding exception indication,
JAVA_PKIX_PATH_BUILDING_FAILED, JAVA_TLS_FAILURE, JAVA_ALGORITHM_PARAMETERS or UNCLASSIFIED.

Native TLS checks may continue to fail until the actual trust source is repaired.
Scheduler requests remain direct HTTP inside loopback tunnels; OpenShift control traffic remains HTTPS.
No scheduler mTLS restoration or certificate-verification bypass is introduced.

## Corporate run and evidence

Use JDK 17 and the project Gradle 8.4 wrapper or an explicitly installed Gradle 8.4 executable.
The current project baseline is Gradle 8.4. Earlier recorded runs retain their actual Gradle versions.

    gradle testClasses --console=plain
    gradle schedulerInfrastructureDiagnostics --console=plain --stacktrace

Run diagnostics only after successful compilation with the required Gradle version.
The existing task/source sets/report pipeline stay unchanged. The complete class now has 14 tests.

Evidence stays at:

    build/regression-results/<env>/scheduler-infrastructure/runs/<id>/
      summary.json
      console.log
      test-results/
      allure-results/
      reports/

The actual raw Allure folder is inside the timestamped run, not a root containing only metadata.
Return the exact run evidence rather than the full corporate project.
These supporting hypotheses do not establish 85% scheduler business regression coverage.

## Primary reference

The standard logs options and timestamp/byte limits are documented at:
https://kubernetes.io/docs/reference/kubectl/generated/kubectl_logs/
The shipped corporate oc version must still be validated by the corporate run.
