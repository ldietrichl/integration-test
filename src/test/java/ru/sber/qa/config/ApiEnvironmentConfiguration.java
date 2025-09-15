package ru.sber.qa.config;

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
import ru.sber.qa.config.services.rest.Allure2RestServiceConfiguration;
import ru.sber.qa.service.ContainerService;
import ru.sber.qa.service.DefaultContainerServiceConfiguration;
import ru.sber.qa.service.DefaultMetricServiceConfiguration;
import ru.sber.qa.service.MetricService;
import ru.sber.qa.services.DefaultOpenSearchServiceConfiguration;
import ru.sber.qa.services.OpenSearchService;
import ru.sber.qa.services.audit.AuditService;
import ru.sber.qa.services.audit.DefaultAuditServiceConfiguration;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.configuration.DefaultConfigurationServiceConfiguration;
import ru.sber.qa.services.db.DatabaseService;
import ru.sber.qa.services.db.DefaultDatabaseServiceConfiguration;
import ru.sber.qa.services.kafka.DefaultKafkaServiceConfiguration;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.pacman.DefaultPacmanServiceConfiguration;
import ru.sber.qa.services.pacman.PacmanService;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.sbermock.DefaultSberMockServiceConfiguration;
import ru.sber.qa.services.sbermock.SberMockService;
import services.grpc.DefaultGrpcServiceConfiguration;
import services.grpc.GrpcService;

public class ApiEnvironmentConfiguration extends DefaultEnvironmentConfiguration {

    @Override
    public @NotNull ServiceConfigurationManager getServiceConfigurations() {
        return super.getServiceConfigurations()
                .put(ConfiguredServiceHolder.of(ConfigurationService.class, new DefaultConfigurationServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(FixtureService.class, new DefaultFixtureServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(ValueService.class))
                .put(ConfiguredServiceHolder.of(DataSourceService.class, new AllureDataSourceServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(DataConverterService.class, new DefaultDataConverterServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(RestService.class, new Allure2RestServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(DatabaseService.class, new DefaultDatabaseServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(KafkaService.class, new DefaultKafkaServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(SberMockService.class, new DefaultSberMockServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(PacmanService.class, new DefaultPacmanServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(ContainerService.class, new DefaultContainerServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(GrpcService.class, new DefaultGrpcServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(TimeoutsService.class, new DefaultTimeoutsServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(InvocationService.class, JUnit5InvocationService.class, new AllureInvocationServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(AuditService.class, new DefaultAuditServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(MetricService.class, new DefaultMetricServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(AuditService.class, new DefaultAuditServiceConfiguration()))
                .put(ConfiguredServiceHolder.of(OpenSearchService.class, new DefaultOpenSearchServiceConfiguration()));
    }
}