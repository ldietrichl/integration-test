package ru.sber.qa.controllers.refBookController;

import config.environment.EnvironmentConfigWithRest;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.allure.CriticalRegression;

import java.util.Map;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
public class GetSplitsTest extends Flows {

    @CriticalRegression
    @Test
    void getRefBookSplitsSuccess() {
        getFlowWithRest()
                .step("Отправка api-запроса, проверка успешного ответа", flow ->
                        flow.restCustomSteps().splitSteps().getRefBookSplits(Map.of("page", 1,
                                        "size", 1))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyMatchesJsonSchemaInClasspath("schemes/refBookSplits.json")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    void getRefBookSplitsBadRequest() {
        getFlowWithRest()
                .step("Отправка api-запроса, проверка bad request ответа", flow ->
                        flow.restCustomSteps().splitSteps().getRefBookSplits(Map.of("page", "",
                                        "size", 1))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                                )
                                .toValidatableJson()
                                .should(
                                        JsonMatchers.haveNotBlankJsonValue("id"),
                                        JsonMatchers.haveJsonValue("message", TextConditions.equalToText("Отсутствует обязательный параметр")),
                                        JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                                ))
                .run();
    }
}
