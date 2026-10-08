package config.extensions;

import infrastructure.kubernetes.ConfigMapState;
import infrastructure.kubernetes.KubernetesWorkloadControl;
import infrastructure.kubernetes.WorkloadAvailabilityProbe;
import infrastructure.kubernetes.WorkloadTarget;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import steps.container.KubernetesWorkloadSteps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JUnit root owns native clients; per-test Platform V AT Environments may close independently. */
public final class WorkloadRunScopeExtension implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace STORE = ExtensionContext.Namespace.create(WorkloadRunScopeExtension.class);
    private static final ThreadLocal<Run> CURRENT = new ThreadLocal<>();

    static final class Run implements ExtensionContext.Store.CloseableResource {
        final Map<WorkloadTarget, KubernetesWorkloadControl> sessions = new LinkedHashMap<>();
        final java.util.Set<WorkloadTarget> failed = new java.util.HashSet<>();

        synchronized void ensure(WorkloadTarget target, java.util.function.Function<KubernetesWorkloadControl, List<ConfigMapState>> stateFactory,
                                 KubernetesWorkloadSteps steps, WorkloadAvailabilityProbe probe) {
            if (failed.contains(target)) throw new IllegalStateException("WORKLOAD_RUN_STATE_FAILED: " + target.workload());
            try {
                KubernetesWorkloadControl session = sessions.get(target);
                if (session == null) {
                    session = steps.session(target);
                    sessions.put(target, session);
                    steps.prepare(session, List.copyOf(stateFactory.apply(session)), probe);
                } else steps.ensureState(session, List.copyOf(stateFactory.apply(session)));
            } catch (RuntimeException | Error failure) {
                failed.add(target);
                throw failure;
            }
        }

        @Override public synchronized void close() {
            List<KubernetesWorkloadControl> reverse = new ArrayList<>(sessions.values());
            java.util.Collections.reverse(reverse);
            Throwable first = null;
            for (KubernetesWorkloadControl session : reverse) {
                try {
                    session.disableAllureAttachments();
                    session.close();
                    if (!session.isRestored()) throw new IllegalStateException("WORKLOAD_RUN_RESTORE_UNCONFIRMED");
                } catch (RuntimeException | Error failure) {
                    if (first == null) first = failure; else first.addSuppressed(failure);
                }
            }
            sessions.clear();
            if (first instanceof Error error) throw error;
            if (first != null) throw (RuntimeException) first;
        }
    }

    @Override public void beforeEach(ExtensionContext context) {
        CURRENT.set(context.getRoot().getStore(STORE).getOrComputeIfAbsent(Run.class, key -> new Run(), Run.class));
    }

    @Override public void afterEach(ExtensionContext context) { CURRENT.remove(); }

    public static void ensure(WorkloadTarget target, List<ConfigMapState> states,
                              KubernetesWorkloadSteps steps, WorkloadAvailabilityProbe probe) {
        List<ConfigMapState> snapshot = List.copyOf(states);
        ensure(target, ignored -> snapshot, steps, probe);
    }

    public static void ensure(WorkloadTarget target,
                              java.util.function.Function<KubernetesWorkloadControl, List<ConfigMapState>> states,
                              KubernetesWorkloadSteps steps, WorkloadAvailabilityProbe probe) {
        Run run = CURRENT.get();
        if (run == null) throw new IllegalStateException("WorkloadRunScopeExtension is required");
        run.ensure(target, states, steps, probe);
    }
}
