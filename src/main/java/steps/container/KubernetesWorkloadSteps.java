package steps.container;

import infrastructure.kubernetes.KubernetesWorkloadControl;
import infrastructure.kubernetes.KubernetesWorkloadService;
import infrastructure.kubernetes.WorkloadTarget;
import infrastructure.kubernetes.ConfigMapState;
import java.util.List;
import java.util.Set;
import static io.qameta.allure.Allure.step;

/** Reusable by any service flow; exact targets and permissions come from the selected stand. */
public final class KubernetesWorkloadSteps {
    private final java.util.function.Function<WorkloadTarget, KubernetesWorkloadControl> sessions;
    public KubernetesWorkloadSteps(KubernetesWorkloadService service) { this(service::open); }
    public KubernetesWorkloadSteps(java.util.function.Function<WorkloadTarget, KubernetesWorkloadControl> sessions) {
        this.sessions = java.util.Objects.requireNonNull(sessions);
    }
    public KubernetesWorkloadControl session(String workload) {
        return session(WorkloadTarget.from(new config.services.core.StandSettings(), workload));
    }
    public KubernetesWorkloadControl session(WorkloadTarget target) {
        return step("Open Fabric8 workload session: " + target.workload(), () -> sessions.apply(target));
    }
    public void checkReady(KubernetesWorkloadControl session) {
        step("Check workload readiness without mutations", () -> session.requireStableReadyWorkload("readiness"));
    }
    public void applyState(KubernetesWorkloadControl session, List<ConfigMapState> states) {
        step("Validate, lock and apply the explicit ConfigMap state", () -> session.applyState(states));
    }
    public KubernetesWorkloadControl prepare(WorkloadTarget target, List<ConfigMapState> states) {
        return prepare(target, states, null);
    }
    public KubernetesWorkloadControl prepare(WorkloadTarget target, List<ConfigMapState> states,
                                            infrastructure.kubernetes.WorkloadAvailabilityProbe probe) {
        List<ConfigMapState> snapshot = List.copyOf(states);
        if (snapshot.isEmpty()) throw new IllegalArgumentException("Managed state must contain ConfigMaps");
        KubernetesWorkloadControl session = session(target);
        return prepare(session, snapshot, probe);
    }
    public KubernetesWorkloadControl prepare(KubernetesWorkloadControl session, List<ConfigMapState> snapshot,
                                            infrastructure.kubernetes.WorkloadAvailabilityProbe probe) {
        try {
            if (probe != null) {
                session.availabilityProbe(probe);
                checkAvailability(session);
            }
            checkReady(session);
            int writes = session.configurationWrites();
            applyState(session, snapshot);
            if (session.configurationWrites() != writes) replacePod(session);
            checkReady(session);
            checkAvailability(session);
            return session;
        } catch (RuntimeException | Error failure) {
            try { session.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    public void checkAvailability(KubernetesWorkloadControl session) {
        step("Await current Service endpoints and read-only application route", session::awaitAvailability);
    }
    public void ensureState(KubernetesWorkloadControl session, List<ConfigMapState> states) {
        step("Verify run ownership and current ConfigMap values", session::requireManagedState);
        checkReady(session);
        int writes = session.configurationWrites();
        applyState(session, states);
        if (session.configurationWrites() != writes) replacePod(session);
        checkReady(session);
        checkAvailability(session);
    }
    public void restore(KubernetesWorkloadControl session) {
        step("Restore owned configuration and confirm readiness", session::close);
    }
    public Set<String> replacePod(KubernetesWorkloadControl session) {
        return step("Delete one owned pod gracefully; await new UID and all containers Ready", session::restart);
    }
    public void inspectConfigMap(KubernetesWorkloadControl session) {
        step("Save private ConfigMap pre-image and inspect Deployment reference without changes", session::inspectConfigMap);
    }
    public void changeConfigMap(KubernetesWorkloadControl session) {
        step("Save ConfigMap, CAS-patch approved key and assert a separate API GET", session::changeConfigMap);
    }
    public void scale(KubernetesWorkloadControl session) {
        step("Change replicas only with explicit stand permission, then await readiness", () -> { session.scale(); });
    }
    public void scale(KubernetesWorkloadControl session, int replicas) {
        step("Scale workload to " + replicas + " replicas", () -> session.scale(replicas));
    }
}
