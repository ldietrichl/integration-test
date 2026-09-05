package config.environment;

import config.services.core.SecureAwareConfigurationService;
import config.services.data.CustomAllureDataSourceServiceConfiguration;
import config.services.rest.CustomAllure2RestV2ServiceConfiguration;
import config.services.rest.config_service.CustomAllure2RestConfigServiceServiceConfiguration;
import config.services.rest.config_service.CustomRestService;
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
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.configuration.DefaultConfigurationServiceConfiguration;
import ru.sber.qa.services.rest.RestService;

public class EnvironmentConfigWithRestConfigService extends DefaultEnvironmentConfiguration {

    @Override
    public @NotNull ServiceConfigurationManager getServiceConfigurations() {
        return super.getServiceConfigurations()
                // Служебные сервисы
                .put(ConfiguredServiceHolder.of(
                        ConfigurationService.class,
                        SecureAwareConfigurationService.class,
                        new DefaultConfigurationServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(FixtureService.class, new DefaultFixtureServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(
                        DataConverterService.class, new DefaultDataConverterServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(ValueService.class))
                .put(ConfiguredServiceHolder.of(
                        DataSourceService.class, new CustomAllureDataSourceServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(TimeoutsService.class, new DefaultTimeoutsServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(
                        InvocationService.class, JUnit5InvocationService.class
                        , new AllureInvocationServiceConfiguration()))

                // Тестовые сервисы
                .put(ConfiguredServiceHolder.of(RestService.class, new CustomAllure2RestV2ServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(CustomRestService.class, new CustomAllure2RestConfigServiceServiceConfiguration()));
    }
}
