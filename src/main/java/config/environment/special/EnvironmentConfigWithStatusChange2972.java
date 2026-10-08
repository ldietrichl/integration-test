package config.environment.special;

import config.services.db.StatusChange2972DatabaseConfiguration;
import config.services.rest.StatusChange2972RestConfiguration;
import config.services.rest.config_service.CustomRestService;
import io.perfeccionista.framework.service.ConfiguredServiceHolder;
import io.perfeccionista.framework.service.ServiceConfigurationManager;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.services.db.DatabaseService;
import ru.sber.qa.services.rest.RestService;

/** Reuses the project's configuration, fixtures, data source, timeouts and Allure invocation services. */
public final class EnvironmentConfigWithStatusChange2972 extends EnvironmentConfigWIthRestDbV2 {
    @Override public @NotNull ServiceConfigurationManager getServiceConfigurations() {
        return super.getServiceConfigurations()
                .put(ConfiguredServiceHolder.of(RestService.class, new StatusChange2972RestConfiguration()))
                .put(ConfiguredServiceHolder.of(CustomRestService.class, new StatusChange2972RestConfiguration.Configurations()))
                .put(ConfiguredServiceHolder.of(config.services.rest.LaunchPlan2972UsersService.class,
                        new config.services.rest.LaunchPlan2972UsersConfiguration()))
                .put(ConfiguredServiceHolder.of(config.services.rest.StatusChange2972ControlService.class,
                        new config.services.rest.StatusChange2972ControlConfiguration()))
                .put(ConfiguredServiceHolder.of(ru.sber.qa.services.kafka.KafkaService.class,
                        new config.services.core.StatusChange2972KafkaConfiguration()))
                .put(ConfiguredServiceHolder.of(DatabaseService.class, new StatusChange2972DatabaseConfiguration()));
    }
}
