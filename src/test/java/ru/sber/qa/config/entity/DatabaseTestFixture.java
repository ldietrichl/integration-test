package ru.sber.qa.config.entity;

import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.invocation.DefaultInvocationServiceConfiguration;
import io.perfeccionista.framework.invocation.InvocationService;
import io.perfeccionista.framework.invocation.runner.InvocationRunner;
import io.perfeccionista.framework.invocation.runner.SingleAttemptInvocationRunner;
import io.perfeccionista.framework.invocation.timeouts.TimeoutsService;
import io.perfeccionista.framework.invocation.timeouts.type.RunInvocationTimeout;
import io.perfeccionista.framework.invocation.wrapper.SingleAttemptInvocationWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.db.DatabaseClient;
import ru.sber.qa.services.db.DatabaseService;

import java.time.Duration;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @deprecated Данный класс необходим для подготовки окружения демонстрационных тестов и неприменим в реальных тестах.
 */
public class DatabaseTestFixture {
    private MockedStatic<Environment> environmentMockedStatic;

    @BeforeEach
    void setUp() {
        //region Подготовка заглушки сервиса ConfigurationService
        Properties properties = new Properties();
        properties.setProperty("url", "jdbc:oracle:thin:@//tklid-asfs00017.vm.mos.cloud.sbrf.ru:1521/fsbmsb");
        properties.setProperty("login", "stub_login");
        properties.setProperty("password", "stub_password");
        properties.setProperty("timeout.in.seconds", "60");
        properties.setProperty("connection.pool.size", "3");
        ConfigurationService configurationServiceMock = mock(ConfigurationService.class);
        when(configurationServiceMock.getProperties(ArgumentMatchers.any())).thenReturn(properties);
        //endregion

        //region Подготовка заглушки сервиса TimeoutsService
        TimeoutsService timeoutsServiceMock = mock(TimeoutsService.class);
        when(timeoutsServiceMock.getTimeout(RunInvocationTimeout.class)).thenReturn(Duration.ofSeconds(60));
        //endregion

        //region Подготовка заглушки сервиса DatabaseClient
        DatabaseClient databaseClientMock = mock(DatabaseClient.class);
        DatabaseTestData databaseTestData = new DatabaseTestData();
        when(databaseClientMock.executeSelect(anyString())).thenReturn(databaseTestData.getTableWithAllRows());
        //endregion

        //region Подготовка заглушки сервиса InvocationService
        InvocationService invocationServiceMock = mock(InvocationService.class);
        when(invocationServiceMock.getInvocationInfoNameFormatter())
                .thenReturn(new DefaultInvocationServiceConfiguration().getInvocationInfoNameFormatter());
        when(invocationServiceMock.getInvocationInfoStatisticsFormatter())
                .thenReturn(new DefaultInvocationServiceConfiguration().getInvocationInfoStatisticsFormatter());
        when(invocationServiceMock.getInvocationRunnerImplementation(SingleAttemptInvocationWrapper.class))
                .thenAnswer((Answer<Class<? extends InvocationRunner>>) invocation -> {
                    return SingleAttemptInvocationRunner.class;
                });
        //endregion

        //region Подготовка заглушки сервиса DatabaseService
        DatabaseService databaseService = mock(DatabaseService.class);
        when(databaseService.dataBaseClient(any())).thenReturn(databaseClientMock);
        //endregion

        //region Подготовка заглушки для Environment
        Environment environmentMock = mock(Environment.class);
        when(environmentMock.getService(InvocationService.class)).thenReturn(invocationServiceMock);
        when(environmentMock.getService(TimeoutsService.class)).thenReturn(timeoutsServiceMock);
        when(environmentMock.getService(DatabaseService.class)).thenReturn(databaseService);
        when(environmentMock.getService(ConfigurationService.class)).thenReturn(configurationServiceMock);

        environmentMockedStatic = Mockito.mockStatic(Environment.class);
        environmentMockedStatic.when(() -> Environment.getForCurrentThread())
                .thenAnswer((Answer<Environment>) invocation -> {
                    return environmentMock;
                });
        //endregion
    }

    @AfterEach
    void tearDown() {
        environmentMockedStatic.close();
    }
}
