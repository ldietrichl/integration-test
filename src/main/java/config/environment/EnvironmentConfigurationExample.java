package config.environment;

import config.services.core.SecureAwareConfigurationService;
import config.services.data.CustomAllureDataSourceServiceConfiguration;
import config.services.db.CustomDatabaseServiceConfiguration;
import config.services.rest.CustomAllure2RestServiceConfiguration;
import io.perfeccionista.framework.DefaultEnvironmentConfiguration;
import io.perfeccionista.framework.datasource.DataConverterService;
import io.perfeccionista.framework.datasource.DataSourceService;
import io.perfeccionista.framework.datasource.DefaultDataConverterServiceConfiguration;
import io.perfeccionista.framework.fixture.DefaultFixtureServiceConfiguration;
import io.perfeccionista.framework.fixture.FixtureService;
import io.perfeccionista.framework.invocation.AllureInvocationServiceConfiguration;
import io.perfeccionista.framework.invocation.InvocationService;
import io.perfeccionista.framework.invocation.JUnit5InvocationService;
import io.perfeccionista.framework.invocation.timeouts.DefaultTimeoutsServiceConfiguration;
import io.perfeccionista.framework.invocation.timeouts.TimeoutsService;
import io.perfeccionista.framework.service.ConfiguredServiceHolder;
import io.perfeccionista.framework.service.ServiceConfigurationManager;
import io.perfeccionista.framework.value.ValueService;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.containers.services.ContainerService;
import ru.sber.qa.containers.services.DefaultContainerServiceConfiguration;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.configuration.DefaultConfigurationServiceConfiguration;
import ru.sber.qa.services.db.DatabaseService;
import ru.sber.qa.services.kafka.DefaultKafkaServiceConfiguration;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.RestService;

@Deprecated
public class EnvironmentConfigurationExample extends DefaultEnvironmentConfiguration {

    @Override
    public @NotNull ServiceConfigurationManager getServiceConfigurations() {
        return super.getServiceConfigurations()
                .put(ConfiguredServiceHolder.of(
                        ConfigurationService.class,
                        SecureAwareConfigurationService.class,
                        new DefaultConfigurationServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(FixtureService.class, new DefaultFixtureServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(ValueService.class))
                .put(ConfiguredServiceHolder.of(
                        DataSourceService.class, new CustomAllureDataSourceServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(
                        DataConverterService.class, new DefaultDataConverterServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(RestService.class, new CustomAllure2RestServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(DatabaseService.class, new CustomDatabaseServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(KafkaService.class, new DefaultKafkaServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(ContainerService.class, new DefaultContainerServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(TimeoutsService.class, new DefaultTimeoutsServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(
                        InvocationService.class, JUnit5InvocationService.class
                        , new AllureInvocationServiceConfiguration()));
    }
}
