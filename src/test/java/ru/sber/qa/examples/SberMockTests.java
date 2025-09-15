package ru.sber.qa.examples;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.sbermock.SberMockClient;
import ru.sber.qa.services.sbermock.SberMockService;
import ru.sbrf.sbermock.api.SberMockApiClient;
import ru.sbrf.sbermock.api.actions.project.Project;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class SberMockTests {

    RestClient restClient;
    SberMockClient sberMockClient;
    SberMockApiClient sberMockApiClient;

    @BeforeEach
    void beforeEach(SberMockService sberMockService, RestService restService) {
        restClient = restService.restClient();
        sberMockClient = sberMockService.sberMockClient("techClientName");
        sberMockApiClient = sberMockClient.getApiClient();
    }

    @Test
    void checkProjectInfoTest() {
        Project project = sberMockApiClient.getProjectsActions().getProject();
        Assertions.assertEquals("BootCampMikhalenkov", project.getName(), "Неверное имя проекта");
        Assertions.assertEquals(72427, project.getId(), "Неверный id проекта");
    }

    @Test
    void getRestConnectionsInfoTest() {
        System.out.println(sberMockClient.getRestTransportConfiguration());
    }

    @Test
    void getRestEmulationsActionsTest() {
        sberMockClient.getRestEmulationActions()
                .getAll()
                .forEach(e -> System.out.printf("%1$-8d %2$-10s %3$s%n", e.getId(), e.getMethod(), e.getName()));
    }

    @Test
    void enableRestEmulationTest() {
        sberMockClient.enableRestEmulation(528447);
        Assertions.assertTrue(sberMockClient.getRestEmulationActions().get(528447)
                .isEnabled());
    }

    @Test
    void disableRestEmulationTest() {
        sberMockClient.disableRestEmulation(528447);
        Assertions.assertFalse(sberMockClient.getRestEmulationActions().get(528447)
                .isEnabled());
    }

    @Test
    void tryRestEmulationTest() {
        sberMockClient.enableRestEmulation(686030);
        Assertions.assertTrue(sberMockClient.getRestEmulationActions().get(686030)
                .isEnabled());
        restClient.get(spec -> spec, "http://testproject00001.sbermock.sigma.sbrf.ru/test")
                .should(RestMatchers.haveBodyWithText("Привет, Сбер!"));
    }

}
