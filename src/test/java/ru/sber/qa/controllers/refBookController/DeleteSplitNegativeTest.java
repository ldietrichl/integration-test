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
public class DeleteSplitNegativeTest extends Flows {

    @CriticalRegression
    @Test
    void cannotDeleteSplitInProgress() {
        getFlowWithRest()
                .step("Создание предусловий", flow -> {
                    SplitsParams params = new SplitsParams();
                    ValidatableJson result =
                            flow.restCustomSteps().splitSteps().createSplit(params)
                                    .should(
                                            RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                    ).toValidatableJson();
                    stash().put("splitId", result.getJsonPath().get("id"));
                })

                .step("Запрос на удаление, ошибка о не остановленном сплите", flow ->
                        flow.restCustomSteps().splitSteps().deleteSplit(stash().get("splitId"))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY))
                                .toValidatableJson()
                                .should(
                                        JsonMatchers.haveNotBlankJsonValue("id"),
                                        JsonMatchers.haveJsonValue("message", TextConditions.equalToText("Для удаления сплит должен быть остановлен")),
                                        JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                                ))

                .step("Удаление тестовых данных", flow -> {
                    SplitsParams params = SplitsParams.builder()
                            .id(((Number) (stash().get("splitId"))).longValue())
                            .status("COMPLETED")
                            .build();
                    flow.restCustomSteps().splitSteps().changeSplit(params)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                            );
                    flow.restCustomSteps().splitSteps().deleteSplit(stash().get("splitId"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    void cannotDeleteSplit() {
        getFlowWithRest()
                .step("Запрос на удаление, проверка ответа о непредвиденной ошибке", flow ->
                        flow.restCustomSteps().splitSteps().deleteSplit("")
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST))
                                .toValidatableJson()
                                .should(
                                        JsonMatchers.haveNotBlankJsonValue("id"),
                                        JsonMatchers.haveJsonValue("message", TextConditions.equalToText("Отсутствует обязательный параметр")),
                                        JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                                ))
                .run();
    }
}
