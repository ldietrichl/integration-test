package ru.sber.qa.controllers.refBookController;

import config.environment.EnvironmentConfigWithRest;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import request.splitter.splits.SplitsParams;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.validation.ValidatableJson;
import ru.sber.qa.allure.CriticalRegression;

import static io.perfeccionista.framework.datasource.Stash.stash;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
public class ChangeSplitNegativeTest extends Flows {

    @CriticalRegression
    @Test
    void changeSplitBadRequest() {
        getFlowWithRest()
                .step("Создание предусловий", flow -> {
                    SplitsParams params = SplitsParams.builder()
                            .status("COMPLETED")
                            .build();
                    ValidatableJson result =
                            flow.restCustomSteps().splitSteps().createSplit(params)
                                    .should(
                                            RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                    ).toValidatableJson();
                    stash().put("splitId", result.getJsonPath().get("id"));
                })

                .step("Запрос на изменение сплита, проверка ответа о непредвиденной ошибке", flow -> {
                    SplitsParams params = SplitsParams.builder()
                            .status(null)
                            .build();
                    flow.restCustomSteps().splitSteps().changeSplit(params)
                            .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST))
                            .toValidatableJson()
                            .should(
                                    JsonMatchers.haveNotBlankJsonValue("id"),
                                    JsonMatchers.haveJsonValue("message", TextConditions.equalToText("Некорректный запрос")),
                                    JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                            );
                })

                .step("Удаление тестовых данных", flow ->
                        flow.restCustomSteps().splitSteps().deleteSplit(stash().get("splitId"))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                .run();
    }
}
