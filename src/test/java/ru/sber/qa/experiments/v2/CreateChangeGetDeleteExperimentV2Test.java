package ru.sber.qa.experiments.v2;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import dto.experiments.v2.ExperimentV2PostRequestDto;
import dto.experiments.v2.ExperimentV2PostRequestDtoBuilder;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.validation.ValidatableJson;
import ru.sber.qa.allure.CriticalRegression;

/***
 * Happy path: успешное создание, изменение, получение и удаление эксперимента V2
 */
@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
public class CreateChangeGetDeleteExperimentV2Test extends Flows {
    private static Long experimentId;
    private static ExperimentV2PostRequestDto body = ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto();

    @CriticalRegression
    @Test
    @Order(10)
    @DisplayName("Успешное создание эксперимента V2")
    void createExperimentV2SuccessTest() {
        getFlowWithDbRest()
                .step("Отправка запроса на создание эксперимента V2", flow -> {

                    ValidatableJson result =
                            flow.restCustomSteps().experimentsV2Steps()
                                    .createOrChangeExperimentV2(body)
                                    .should(
                                            RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                    ).toValidatableJson();
                    experimentId = result.getJsonPath().getLong("id");
                })
                .step("Проверка, что эксперимент создан в БД", flow ->
                        flow.dbExpLabClient()
                                .executeSelect("""
                                        SELECT * FROM experiments.experiment WHERE id = %s
                                        
                                        """.formatted(experimentId))
                                .should(DatabaseMatchers.tableHaveSize(1)))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(20)
    @DisplayName("Успешное изменение эксперимента V2")
    void changeExperimentV2SuccessTest() {
        getFlowWithDbRest()
                .step("Отправка запроса на создание эксперимента V2", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .createOrChangeExperimentV2(body.toBuilder()
                                        .id(experimentId)
                                        .updateMetrics(true)
                                        .build())
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyJsonValueEqualTo("updateMetrics", "true")

                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(30)
    @DisplayName("Успешное получение эксперимента по id V2")
    void getExperimentV2SuccessTest() {
        getFlowWithDbRest()
                .step("Отправка запроса на создание эксперимента V2", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .getExperimentV2ById(experimentId)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(40)
    @DisplayName("Успешное удаление эксперимента V2")
    void deleteExperimentV2SuccessTest() {
        getFlowWithDbRest()
                .step("Отправка запроса на создание эксперимента V2", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .deleteExperimentV2ById(experimentId)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                ))
                .step("Проверка, что эксперимент удалён из БД", flow ->
                        flow.dbExpLabClient()
                                .executeSelect("""
                                        SELECT * FROM experiments.experiment WHERE id = %s
                                        
                                        """.formatted(experimentId))
                                .should(DatabaseMatchers.tableHaveSize(0)))
                .run();
    }
}
