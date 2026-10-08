package steps.container;

import config.extensions.WorkloadRunScopeExtension;
import infrastructure.kubernetes.*;
import java.util.*;
import java.util.function.Function;

/** Scenario lifecycle over the single framework Fabric8 service. No client or process is created here. */
public final class WorkloadScenarioSteps {
    private final KubernetesWorkloadSteps steps;
    public WorkloadScenarioSteps(KubernetesWorkloadSteps steps) { this.steps = Objects.requireNonNull(steps); }

    public KubernetesWorkloadControl prepare(WorkloadTarget target,
            Function<KubernetesWorkloadControl, List<ConfigMapState>> desiredState,
            WorkloadAvailabilityProbe availability, WorkloadScenarioEvidence evidence, boolean restart) {
        KubernetesWorkloadControl[] selected = new KubernetesWorkloadControl[1];
        try {
            WorkloadRunScopeExtension.ensure(target, session -> {
                selected[0] = session;
                evidence.bind(session);
                evidence.event("CHECK_CONFIGMAP_AND_ENSURE_PROFILE");
                var state = WorkloadStates.merge(desiredState.apply(session));
                evidence.configMaps(state);
                return state;
            }, steps, availability);
            var session = Objects.requireNonNull(selected[0]);
            if (restart) restart(session);
            evidence.prepared();
            return session;
        } catch (RuntimeException | Error failure) { evidence.failed(failure); throw failure; }
    }

    public void restart(KubernetesWorkloadControl session) {
        session.captureEvidence("before pod replacement");
        steps.replacePod(session);
        steps.checkReady(session);
        steps.checkAvailability(session);
    }

    public void collectEvidence() {
        var evidence = WorkloadScenarioEvidence.currentOrNull();
        if (evidence != null) evidence.finish();
    }
}
