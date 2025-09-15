package ru.sber.qa.config.environment;

import io.perfeccionista.framework.DefaultEnvironmentConfiguration;
import io.perfeccionista.framework.invocation.AllureInvocationServiceConfiguration;
import io.perfeccionista.framework.invocation.InvocationService;
import io.perfeccionista.framework.invocation.JUnit5InvocationService;
import io.perfeccionista.framework.invocation.timeouts.DefaultTimeoutsServiceConfiguration;
import io.perfeccionista.framework.invocation.timeouts.TimeoutsService;
import io.perfeccionista.framework.service.ConfiguredServiceHolder;
import io.perfeccionista.framework.service.ServiceConfigurationManager;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.config.services.rest.Allure2RestServiceConfiguration;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.configuration.DefaultConfigurationServiceConfiguration;
import ru.sber.qa.services.journal.DefaultJournalServiceConfiguration;
import ru.sber.qa.services.journal.JournalService;
import ru.sber.qa.services.rest.RestService;

/**
 * Стандартная конфигурация окружения сервиса {@link JournalService} для проверки в тестах, которые используют
 * реальное взаимодействие с источником.
 */
public class JournalEnvConfigStandart extends DefaultEnvironmentConfiguration {

    @Override
    public @NotNull ServiceConfigurationManager getServiceConfigurations() {
        return ServiceConfigurationManager.of()
                .put(ConfiguredServiceHolder.of(ConfigurationService.class,
                        new DefaultConfigurationServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(InvocationService.class, JUnit5InvocationService.class,
                        new AllureInvocationServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(RestService.class, new Allure2RestServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(TimeoutsService.class, new DefaultTimeoutsServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(JournalService.class, new DefaultJournalServiceConfiguration()));
    }
}
