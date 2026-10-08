package config.environment.special;

import config.services.container.DiagnosticContainerServiceConfiguration;
import io.perfeccionista.framework.DefaultEnvironmentConfiguration;
import io.perfeccionista.framework.invocation.AllureInvocationServiceConfiguration;
import io.perfeccionista.framework.invocation.InvocationService;
import io.perfeccionista.framework.invocation.JUnit5InvocationService;
import io.perfeccionista.framework.service.ConfiguredServiceHolder;
import io.perfeccionista.framework.service.ServiceConfigurationManager;
import ru.sber.qa.containers.services.ContainerService;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.configuration.DefaultConfigurationServiceConfiguration;
import io.perfeccionista.framework.invocation.timeouts.TimeoutsService;
import io.perfeccionista.framework.invocation.timeouts.DefaultTimeoutsServiceConfiguration;

/** No DB, Kafka, REST, tunnel or browser initialization in the authentication probe. */
public final class EnvironmentConfigWithContainerDiagnostics extends DefaultEnvironmentConfiguration {
        @Override
    public ServiceConfigurationManager getServiceConfigurations() {
        return super.getServiceConfigurations()
                .put(ConfiguredServiceHolder.of(
                        ConfigurationService.class,
                        new DefaultConfigurationServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(
                        TimeoutsService.class,
                        new DefaultTimeoutsServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(
                        ContainerService.class,
                        new DiagnosticContainerServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(
                        InvocationService.class,
                        JUnit5InvocationService.class,
                        new AllureInvocationServiceConfiguration()));
    }
}
