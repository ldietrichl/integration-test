package ru.sber.qa.controllers.refBookController;

import config.environment.EnvironmentConfigWithRest;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.allure.CriticalRegression;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
public class CjLinksTest extends Flows {

    @CriticalRegression
    @Test
    void cjLinksSuccess() {
        getFlowWithRest()
                .step("Отправка запроса, проверка успешного ответа", flow -> {
                    String body =
                            """
                                    {"page":0,
                                    "size":15}
                                    """;
                    flow.restCustomSteps().splitSteps().getCjLinks(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                    RestMatchers.haveBodyMatchesJsonSchemaInClasspath("schemes/cjLinks.json")
                            );
                }).run();
    }

    @CriticalRegression
    @Test
    void cjLinksBadRequest() {
        getFlowWithRest()
                .step("Отправка запроса, проверка bad request ответа", flow -> {
                    String body = "{\"size\":15}";
                    flow.restCustomSteps().splitSteps().getCjLinks(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                            );
                }).run();
    }
}
