package infrastructure.kubernetes;

import config.services.container.KubernetesTunnelSettings;
import config.services.core.StandSettings;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.service.Service;
import io.perfeccionista.framework.service.ServiceConfiguration;

/** Framework entry point. Sessions own native clients and may outlive the per-test Environment. */
public final class KubernetesWorkloadService implements Service {
    private Environment environment;

    @Override public void init(Environment environment) { this.environment = environment; }
    @Override public void init(Environment environment, ServiceConfiguration configuration) { init(environment); }

    public KubernetesWorkloadControl open(String workload) {
        return open(WorkloadTarget.from(new StandSettings(), workload));
    }

    public KubernetesWorkloadControl open(WorkloadTarget target) {
        if (environment == null) throw new IllegalStateException("WORKLOAD_ENVIRONMENT_NOT_INITIALIZED");
        KubernetesTunnelSettings connection = KubernetesTunnelSettings.from(environment)
                .withService(target.service(), target.port(), target.container());
        return new KubernetesWorkloadControl(connection, target.workload());
    }
}
