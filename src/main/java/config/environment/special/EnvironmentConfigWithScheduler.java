package config.environment.special;

import config.environment.EnvironmentConfigWithDbRest;
import config.services.rest.SchedulerProbeRestService;
import config.services.rest.SchedulerRestConfiguration;
import io.perfeccionista.framework.service.ConfiguredServiceHolder;
import io.perfeccionista.framework.service.ServiceConfigurationManager;
import ru.sber.qa.services.rest.RestService;

/** Retains project fixtures, database, secure configuration, invocation and Allure services. */
public class EnvironmentConfigWithScheduler extends EnvironmentConfigWithDbRest {
    @Override public ServiceConfigurationManager getServiceConfigurations() {
        boolean tunnel = new config.services.core.SchedulerSettings().tunnelEnabled();
        ServiceConfigurationManager configurations = super.getServiceConfigurations();
        configurations.put(ConfiguredServiceHolder.of(
                io.perfeccionista.framework.invocation.InvocationService.class,
                io.perfeccionista.framework.invocation.JUnit5InvocationService.class,
                new config.services.invocation.SchedulerAllureInvocationConfiguration()));
        configurations.put(ConfiguredServiceHolder.of(RestService.class,
                tunnel ? new config.services.rest.SchedulerTunnelRestConfiguration()
                        : new SchedulerRestConfiguration()));
        if (tunnel || config.services.container.KubernetesTunnelConfigScope.enabled()) {
            configurations
                .put(ConfiguredServiceHolder.of(SchedulerProbeRestService.class,
                        new config.services.rest.SchedulerTunnelRestConfiguration()))
                .put(ConfiguredServiceHolder.of(ru.sber.qa.containers.services.ContainerService.class,
                        new config.services.container.KubeconfigContainerServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(infrastructure.kubernetes.KubernetesTunnelService.class));
        }
        return configurations;
    }
    public static final class Managed extends EnvironmentConfigWithScheduler { }
}
