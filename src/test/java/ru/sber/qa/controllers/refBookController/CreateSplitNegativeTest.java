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
import ru.sber.qa.allure.CriticalRegression;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
public class CreateSplitNegativeTest extends Flows {

    @CriticalRegression
    @Test
    void createSplitWrongStructureOfGroup() {
        getFlowWithRest()
                .step("Отправка запроса на создание сплита, проверка ответа о неверной структуре групп", flow -> {
                    SplitsParams params = SplitsParams.builder()
                            .groups("""
                                     [{"groupCode":,"shareFrom":,"size":}]
                                    """)
                            .build();
                    flow.restCustomSteps().splitSteps().createSplit(params)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                            ).toValidatableJson()
                            .should(
                                    JsonMatchers.haveNotBlankJsonValue("id"),
                                    JsonMatchers.haveJsonValue("message", TextConditions.equalToText("Неверная структура описания групп")),
                                    JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                            );
                }).run();
    }

    @CriticalRegression
    @Test
    void createSplitSameGroupCode() {
        getFlowWithRest()
                .step("Отправка запроса на создание сплита, проверка ответа о не уникальности кодов групп", flow -> {
                    SplitsParams params = SplitsParams.builder()
                            .groups("""
                                     [{"groupCode": "A","shareFrom": 0,"size": 75}, {"groupCode": "A","shareFrom": 0,"size": 75}]
                                    """)
                            .build();
                    flow.restCustomSteps().splitSteps().createSplit(params)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                            ).toValidatableJson()
                            .should(
                                    JsonMatchers.haveNotBlankJsonValue("id"),
                                    JsonMatchers.haveJsonValue("message", TextConditions.equalToText("Не уникальны коды групп")),
                                    JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                            );
                }).run();
    }

    @CriticalRegression
    @Test
    void createSplitBadRequest() {
        getFlowWithRest()
                .step("Отправка запроса на создание сплита, проверка ответа о непредвиденной ошибке", flow -> {
                    SplitsParams params = SplitsParams.builder()
                            .groups(null)
                            .build();
                    flow.restCustomSteps().splitSteps().createSplit(params)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                            ).toValidatableJson()
                            .should(
                                    JsonMatchers.haveNotBlankJsonValue("id"),
                                    JsonMatchers.haveJsonValue("message", TextConditions.equalToText("Некорректный запрос")),
                                    JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                            );
                }).run();
    }
}
