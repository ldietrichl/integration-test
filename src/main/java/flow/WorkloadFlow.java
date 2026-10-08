package flow;

import infrastructure.kubernetes.KubernetesWorkloadService;
import io.perfeccionista.framework.Environment;
import steps.container.KubernetesWorkloadSteps;

/** Common project Flow extension following platform-v-at service lookup conventions. */
public interface WorkloadFlow {
    default steps.container.WorkloadScenarioSteps workloadScenarioSteps() {
        return new steps.container.WorkloadScenarioSteps(workloadSteps());
    }

    default KubernetesWorkloadSteps workloadSteps() {
        return new KubernetesWorkloadSteps(Environment.getForCurrentThread()
                .getService(KubernetesWorkloadService.class));
    }
}
