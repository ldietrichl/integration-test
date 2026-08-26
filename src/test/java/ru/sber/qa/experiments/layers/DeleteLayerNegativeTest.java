package ru.sber.qa.experiments.layers;

import config.environment.EnvironmentConfigWithRest;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.allure.CriticalRegression;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
public class DeleteLayerNegativeTest extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("Удаление слоя - ошибка 'Слой не найден'")
    void deleteLayerNotFoundTest() {


        getFlowWithRest()
                .step("Отправка запроса на удаление слоя -> ошибка 'Слой не найден'", flow ->
                        flow.restCustomSteps().layerSteps().deleteLayerById(100000000L)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                                ).toValidatableJson()
                                .should(
                                        JsonMatchers.haveNotBlankJsonValue("id"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Слой с id=100000000 не найден")),
                                        JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                                ))
                .run();
    }
}
