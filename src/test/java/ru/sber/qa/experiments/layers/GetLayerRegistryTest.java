package ru.sber.qa.experiments.layers;

import config.environment.EnvironmentConfigWithRest;
import dto.experiments.layers.LayerRegistryGetRequestDtoBuilder;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.allure.CriticalRegression;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
public class GetLayerRegistryTest extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("Получение реестра слоев - успешный сценарий")
    void getLayerRegistrySuccessTest() {


        getFlowWithRest()
                .step("Отправка запроса на получение реестра слоев", flow ->
                        flow.restCustomSteps().layerSteps()
                                .getLayerRegistry(LayerRegistryGetRequestDtoBuilder.buildDefaultDto())
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                ).toValidatableJson()
                                .should(
                                        JsonMatchers.haveNotBlankJsonValue("totalPages"),
                                        JsonMatchers.evaluateJsonPathExpression("content!=null")
                                ))
                .run();
    }
}
