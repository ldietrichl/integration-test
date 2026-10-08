package config.environment.special;

import config.services.container.KubeconfigContainerServiceConfiguration;
import infrastructure.kubernetes.KubernetesTunnelService;
import io.perfeccionista.framework.service.ConfiguredServiceHolder;
import io.perfeccionista.framework.service.ServiceConfigurationManager;
import ru.sber.qa.containers.services.ContainerService;

/** Scoped to the hypothesis suite: existing scheduler regressions are not silently switched to tunnels. */
public final class EnvironmentConfigWithSchedulerInfrastructure extends EnvironmentConfigWithScheduler {
    @Override
    public ServiceConfigurationManager getServiceConfigurations() {
        return super.getServiceConfigurations()
                .put(ConfiguredServiceHolder.of(ContainerService.class, new KubeconfigContainerServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(KubernetesTunnelService.class));
    }
}
