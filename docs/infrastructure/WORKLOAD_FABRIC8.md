# Shared Fabric8 workload management

The project keeps the platform-v-at architecture:

`FlowRunner -> WorkloadFlow -> KubernetesWorkloadSteps -> KubernetesWorkloadService -> KubernetesWorkloadControl -> Fabric8`

`KubernetesWorkloadService` is registered with `ConfiguredServiceHolder` in the existing DB/REST and example environments. Registration does not create a client or connect to a cluster. Custom environments must register the same service. Session creation happens inside an initialized test Environment. The native client and rollback do not depend on thread-local framework invocation state.

## Service profile

Connection identity, TLS and credentials continue to come from the existing stand configuration. No new credential source is introduced.

```properties
stand.dev.workloads.example.deployment=example-service
stand.dev.workloads.example.service=example-service
stand.dev.workloads.example.service-port=8080
stand.dev.workloads.example.container=example-service
stand.dev.workloads.example.configmap=example-service
```

`configmap` is the stable workload lock target. All configurations of the same workload must use the same lock target, even when the requested changes touch different ConfigMaps. Each requested ConfigMap is checked against the Deployment's container/init-container environment and mounted regular/projected volumes. Its name is explicit, never inferred from a `-lib` suffix.

The existing stand mutation permissions remain required. Read-only readiness does not require pod-delete permission. Native ConfigMap/pod management does not require portforward; a tunnel still requires its own permission. An OkHttp HTTP provider for Fabric8 6.13.4 is now an explicit runtime dependency.

## Scenario integration

```java
var steps = flow.workloadSteps(); // The project's flow implements WorkloadFlow.
var target = new WorkloadTarget("example", "example-service", 8080, "example-service");
var state = new ConfigMapState("example-service", Map.of("FEATURE_ENABLED", "false"));
try (var session = steps.prepare(target, List.of(state))) {
    // Existing business steps and assertions.
}
```

`ConfigMapState.resource(name, dataKey, classpathResource)` replaces one complete textual document. `dataKey` is an exact ConfigMap data key, not a dotted YAML path. All keys/values are validated before mutations. Nested YAML editing is not provided by this API.

Low-level steps are `session`, `checkReady`, `applyState`, `replacePod`, `scale(session, replicas)` and `restore`. `prepare` checks readiness, locks the workload and all requested ConfigMaps in deterministic order, applies values with CAS and separate GET assertions, replaces the pod and checks readiness again. It closes the session on a preparation failure and preserves both primary and cleanup errors.

Kubernetes readiness and application readiness are distinct. `checkReady` checks Deployment/pod readiness. Service-specific HTTP/business assertions remain in the service's steps. `WorkloadReadinessProbe` provides the captured restoration callback used by Scheduler.

## Migration and lifecycle

- Existing Scheduler workload calls now resolve the shared service. Job permissions, fixture windows, final application checks and run-level restoration remain compatible.
- EXPLAB-2690 uses shared preparation from instance `BeforeEach`, after Perfeccionista initialization, and restores from `AfterEach`. There is no shared static session. Each selected test invocation gets an independent state and may cause an apply/recovery pair of pod replacements.
- The existing Scheduler run scope still owns its long-lived baseline. The shared service does not close a run-owned session at the end of an individual test.
- Diagnostic tests of the platform's own ContainerService keep using that API intentionally.
- The old Scheduler lease adapter and annotation name remain compatible with existing runs. Common Kubernetes classes no longer import Scheduler REST or readiness implementations.

## Guarantees and limits

Owned keys are restored in reverse order. Concurrent changes to an owned key are preserved and reported as a recovery conflict. A partial or ambiguous PATCH is resolved by reading actual state before rollback. Changes applied to a pod are rolled back with another replacement and readiness confirmation. Failure to confirm recovery retains the lease and makes cleanup fail; it is not reported as restored.

Private full snapshots stay under `build/stand-artifacts` with restricted ACL. Reports expose identity and redacted state. Abrupt process termination still requires inspection of saved snapshots and the retained lease; no in-process cleanup can guarantee recovery after process kill.

This version supports the existing stable single-replica Deployment mutation workflow. Read-only readiness supports multiple replicas, but automatic rolling replacement of multi-replica workloads and StatefulSets is not implemented. `scale` retains the existing explicit permission/HPA/quota checks.

## Verification

```powershell
.\gradlew.bat testClasses workloadContractChecks
.\gradlew.bat explab2690MapperCoverage --dry-run
```

`workloadContractChecks` uses a loopback HTTP API and real Fabric8 requests. It checks native client creation without Environment, config ownership, CAS, multi-ConfigMap partial failures, ambiguous writes, rollback conflicts, added-key removal, pod identity, idempotent close and the existing Scheduler isolation contracts. It makes no corporate Kubernetes or database requests. The test needs filesystem permission to create private snapshot ACLs.

Live acceptance remains separate: execute Scheduler diagnostics and EXPLAB-2690 on the configured corporate stand and confirm both workload readiness and final application behavior.
