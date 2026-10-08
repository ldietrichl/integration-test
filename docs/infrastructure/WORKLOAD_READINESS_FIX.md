# Workload readiness after pod replacement

The EXPLAB-2690 managed-state path now uses the existing WorkloadFlow / service / steps architecture:

1. Capture a read-only application probe before opening a mutation session.
2. Await Deployment/pod readiness, current Service endpoints and two successful route samples before mutation.
3. Apply the explicitly selected ConfigMap, replace the pod, then repeat the availability gate.
4. Run the scenario without replaying business POST requests.
5. Restore owned ConfigMap fields and replace the pod; confirm availability before releasing the lease.

`KubernetesWorkloadSteps.prepare(target, states, probe)` is the reusable overload. Existing two-argument callers and Scheduler root probes keep their previous behavior. Other services supply their own read-only `WorkloadAvailabilityProbe`. Register it before mutations; each sample must have a bounded request timeout and must not change state.

For EXPLAB-2690 the probe uses GET `SplitterEndpointPaths.mapperVersion()` (default `/api/v1/splitter/mapper/version`) and the same `EXPLAB_GATEWAY` base URI as the business RestClient. Existing mapper endpoint overrides are respected. It requires the existing strict stand mTLS configuration and approved ingress host. No relaxed TLS, credential edits, redirects or HTTP logging filters are added. The callback captures TLS settings before mutation and can run during cleanup without a thread-local Environment.

## Kubernetes and HTTP policy

In addition to existing permissions, the selected namespace needs `get endpoints` and `get services`. The patch does not grant RBAC or alter kubeconfig. It uses core/v1 Endpoints; EndpointSlice-only clusters need an additional adapter before this gate can succeed.

Endpoints must match the selected Service port (including a named targetPort), its selector and the current owned pod UIDs. Missing endpoints, stale UIDs or not-ready addresses are not accepted as ready. API/RBAC failures fail with safe diagnostics, without retrying mutations.

Only GET samples returning 502/503/504 are retried. Two consecutive HTTP 200 samples are required. Other statuses, including 401/403/404/500, and transport/TLS errors fail immediately. An HTTP 200 version response proves route reachability, not business correctness or that every endpoint of the service is ready. Business assertions are unchanged.

Settings for each workload in stand.properties (or existing property overrides):

```properties
stand.dev.workloads.splitter-mapper.readiness.timeout.seconds=120
stand.dev.workloads.splitter-mapper.readiness.poll.millis=2000
```

The deadline is checked between samples; in-flight HTTP calls have 2-second connect/socket timeouts, Kubernetes calls retain their configured request timeout. An in-flight request can therefore finish after the polling deadline. Interruption is preserved. A transient failed sample resets the consecutive-success counter.

The ticket task accepts the same keys with `-P`. It remains in `gradle/build-logic/tickets.gradle.kts`, group `tickets`. It still prepares and restores per test invocation; this patch does not reduce the number of pod replacements.

Readiness failures use `WORKLOAD_READINESS_*` and are classified `BLOCKED_ENVIRONMENT`, not business assertion failures. Exception messages include safe exception type names, never original credential-bearing messages. This does not retroactively reclassify old reports or generic business HTTP assertions.

If restoration availability fails, configuration may already be restored, but the session is not marked successful and the lease is retained. Inspect private `build/stand-artifacts` and route readiness before recovery; do not automatically remove the lease or rerun mutations.

## Validation

`testClasses workloadContractChecks` compiles the project and runs isolated loopback API/HTTP checks, including stale endpoints, delayed 503 -> 200, timeout, interruption and restoration before lease release. No real cluster or database is used. Corporate acceptance still requires running the ticket task on the configured stand.
