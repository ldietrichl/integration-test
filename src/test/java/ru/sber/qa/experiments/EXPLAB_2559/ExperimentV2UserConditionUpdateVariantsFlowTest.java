package ru.sber.qa.experiments.EXPLAB_2559;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import dto.experiments.v2.ExperimentV2PostRequestDto;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.validation.ValidatableJson;

import java.util.List;

import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentGroups;
import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto;
import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultObjectSelectCondition;
import static ru.sber.qa.experiments.EXPLAB_2559.ExperimentV2UserConditionScopeAssumptions.assumeMapperScopeAvailable;

/**
 * EXPLAB-2559.
 * Дополнительные update-сценарии для objectSelectConditions[].userCondition.
 */
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
@ResourceLock("explab-2559-user-condition")
public class ExperimentV2UserConditionUpdateVariantsFlowTest extends Flows {


    @Test
    @DisplayName("EXPLAB-2559. Обновление userCondition: длинное значение заменяется коротким")
    void updateExperimentV2UserConditionFromLongToShortSuccessTest() {
        assumeMapperScopeAvailable();

        String createUserCondition = fixedLengthUserCondition(1000, "CREATE_LONG");
        String updateUserCondition = fixedLengthUserCondition(100, "UPDATE_SHORT");

        createUpdateCheckAndDelete(createUserCondition, updateUserCondition, 100,
                "Обновляем эксперимент V2: длинный userCondition заменяем коротким");
    }


    @Test
    @DisplayName("EXPLAB-2559. Обновление userCondition: длинное значение заменяется другим длинным")
    void updateExperimentV2UserConditionFromLongToAnotherLongSuccessTest() {
        assumeMapperScopeAvailable();

        String createUserCondition = fixedLengthUserCondition(512, "CREATE_LONG");
        String updateUserCondition = fixedLengthUserCondition(2000, "UPDATE_LONG");

        createUpdateCheckAndDelete(createUserCondition, updateUserCondition, 2000,
                "Обновляем эксперимент V2: длинный userCondition заменяем другим длинным");
    }


    @Test
    @DisplayName("EXPLAB-2559. Обновление эксперимента V2 с двумя objectSelectConditions[].userCondition")
    void updateExperimentV2WithMultipleUserConditionsSuccessTest() {
        assumeMapperScopeAvailable();

        final Long[] experimentId = new Long[1];
        final boolean[] deleted = {false};

        String firstCreateUserCondition = fixedLengthUserCondition(300, "FIRST_CREATE");
        String secondCreateUserCondition = fixedLengthUserCondition(512, "SECOND_CREATE");
        String firstUpdateUserCondition = fixedLengthUserCondition(700, "FIRST_UPDATE");
        String secondUpdateUserCondition = fixedLengthUserCondition(1000, "SECOND_UPDATE");

        try {
            getFlowWithDbRest()
                    .step("Создаем эксперимент V2 с двумя objectSelectConditions", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .createOrChangeExperimentV2(buildExperimentWithTwoUserConditions(
                                        firstCreateUserCondition,
                                        secondCreateUserCondition
                                ))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        experimentId[0] = result.getJsonPath().getLong("id");
                        Assertions.assertNotNull(experimentId[0], "В ответе создания эксперимента V2 должен вернуться id");
                    })
                    .step("Обновляем оба objectSelectConditions[].userCondition", flow ->
                            flow.restCustomSteps().experimentsV2Steps()
                                    .createOrChangeExperimentV2(buildExperimentWithTwoUserConditions(
                                            firstUpdateUserCondition,
                                            secondUpdateUserCondition
                                    ).toBuilder()
                                            .id(experimentId[0])
                                            .build())
                                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                    .step("Получаем обновленный эксперимент V2 и проверяем оба значения userCondition", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .getExperimentV2ById(experimentId[0])
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        List<String> actualUserConditions = result.getJsonPath()
                                .getList("objectSelectConditions.userCondition", String.class);

                        Assertions.assertNotNull(actualUserConditions,
                                "GET /api/v2/experiments/{id} должен вернуть массив objectSelectConditions.userCondition");
                        Assertions.assertTrue(actualUserConditions.contains(firstUpdateUserCondition),
                                "GET /api/v2/experiments/{id} должен вернуть обновленный первый userCondition");
                        Assertions.assertTrue(actualUserConditions.contains(secondUpdateUserCondition),
                                "GET /api/v2/experiments/{id} должен вернуть обновленный второй userCondition");
                    })
                    .step("Проверяем в БД, что оба обновленных userCondition сохранены полностью", flow ->
                            flow.dbExpLabClient()
                                    .executeSelect("""
                                            SELECT id
                                            FROM experiments.exp_object_select_condition
                                            WHERE exp_id = %s
                                              AND (
                                                  (condition_no = 1 AND user_condition = '%s' AND length(user_condition) = 700)
                                                  OR
                                                  (condition_no = 2 AND user_condition = '%s' AND length(user_condition) = 1000)
                                              )
                                            """.formatted(experimentId[0], firstUpdateUserCondition, secondUpdateUserCondition))
                                    .should(DatabaseMatchers.tableHaveSize(2)))
                    .step("Удаляем созданный для EXPLAB-2559 эксперимент V2", flow ->
                            flow.restCustomSteps().experimentsV2Steps()
                                    .deleteExperimentV2ById(experimentId[0])
                                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                    .step("Проверяем, что эксперимент удален из БД", flow -> {
                        flow.dbExpLabClient()
                                .executeSelect(selectExperimentByIdSql(experimentId[0]))
                                .should(DatabaseMatchers.tableHaveSize(0));
                        deleted[0] = true;
                    })
                    .run();
        } finally {
            if (experimentId[0] != null && !deleted[0]) {
                cleanupExperimentSilently(experimentId[0]);
            }
        }
    }

    private void createUpdateCheckAndDelete(String createUserCondition,
                                            String updateUserCondition,
                                            int expectedLength,
                                            String updateStepDescription) {
        final Long[] experimentId = new Long[1];
        final boolean[] deleted = {false};

        try {
            getFlowWithDbRest()
                    .step("Создаем эксперимент V2 с исходным userCondition", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .createOrChangeExperimentV2(buildExperimentWithUserCondition(createUserCondition))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        experimentId[0] = result.getJsonPath().getLong("id");
                        Assertions.assertNotNull(experimentId[0], "В ответе создания эксперимента V2 должен вернуться id");
                    })
                    .step(updateStepDescription, flow ->
                            flow.restCustomSteps().experimentsV2Steps()
                                    .createOrChangeExperimentV2(buildExperimentWithUserCondition(updateUserCondition).toBuilder()
                                            .id(experimentId[0])
                                            .build())
                                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                    .step("Получаем обновленный эксперимент V2 и проверяем новое значение userCondition", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .getExperimentV2ById(experimentId[0])
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        String actualUserCondition = result.getJsonPath()
                                .getString("objectSelectConditions[0].userCondition");

                        assertUserCondition(actualUserCondition, updateUserCondition, expectedLength,
                                "GET /api/v2/experiments/{id} должен вернуть обновленный userCondition");
                    })
                    .step("Проверяем в БД, что сохранено новое значение userCondition", flow ->
                            flow.dbExpLabClient()
                                    .executeSelect(selectUserConditionSql(experimentId[0], 1, updateUserCondition, expectedLength))
                                    .should(DatabaseMatchers.tableHaveSize(1)))
                    .step("Удаляем созданный для EXPLAB-2559 эксперимент V2", flow ->
                            flow.restCustomSteps().experimentsV2Steps()
                                    .deleteExperimentV2ById(experimentId[0])
                                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                    .step("Проверяем, что эксперимент удален из БД", flow -> {
                        flow.dbExpLabClient()
                                .executeSelect(selectExperimentByIdSql(experimentId[0]))
                                .should(DatabaseMatchers.tableHaveSize(0));
                        deleted[0] = true;
                    })
                    .run();
        } finally {
            if (experimentId[0] != null && !deleted[0]) {
                cleanupExperimentSilently(experimentId[0]);
            }
        }
    }

    private void cleanupExperimentSilently(Long experimentId) {
        try {
            getFlowWithDbRest().flow().restCustomSteps().experimentsV2Steps()
                    .deleteExperimentV2ById(experimentId);
        } catch (RuntimeException ignored) {
            // Best-effort cleanup не должен маскировать основную ошибку теста.
        }
    }

    private static ExperimentV2PostRequestDto buildExperimentWithUserCondition(String userCondition) {
        ExperimentV2PostRequestDto.ObjectSelectCondition condition = buildObjectSelectCondition(1, userCondition);

        return buildDefaultExperimentV2PostRequestDto().toBuilder()
                .objectSelectConditions(List.of(condition))
                .build();
    }

    private static ExperimentV2PostRequestDto buildExperimentWithTwoUserConditions(String firstUserCondition,
                                                                                   String secondUserCondition) {
        ExperimentV2PostRequestDto.ExperimentGroups group = buildDefaultExperimentGroups().toBuilder()
                .splittingResults(List.of(buildSplittingResult(1), buildSplittingResult(2)))
                .build();

        return buildDefaultExperimentV2PostRequestDto().toBuilder()
                .objectSelectConditions(List.of(
                        buildObjectSelectCondition(1, firstUserCondition),
                        buildObjectSelectCondition(2, secondUserCondition)
                ))
                .experimentGroups(List.of(group))
                .build();
    }

    private static ExperimentV2PostRequestDto.ObjectSelectCondition buildObjectSelectCondition(int number,
                                                                                                String userCondition) {
        return buildDefaultObjectSelectCondition().toBuilder()
                .number(number)
                .userCondition(userCondition)
                .build();
    }

    private static ExperimentV2PostRequestDto.ExperimentGroups.SplittingResult buildSplittingResult(int conditionNumber) {
        return ExperimentV2PostRequestDto.ExperimentGroups.SplittingResult.builder()
                .conditionNumber(conditionNumber)
                .userResult("actionType: 0")
                .resultParams(List.of(
                        ExperimentV2PostRequestDto.ExperimentGroups.SplittingResult.ResultParam.builder()
                                .paramCode("actionType")
                                .paramValue(List.of("0"))
                                .dataType("INTEGER")
                                .build()
                ))
                .build();
    }

    private static void assertUserCondition(String actualUserCondition,
                                            String expectedUserCondition,
                                            int expectedLength,
                                            String message) {
        Assertions.assertNotNull(actualUserCondition, message + ": значение не должно быть null");
        Assertions.assertEquals(expectedUserCondition, actualUserCondition, message);
        Assertions.assertEquals(expectedLength, actualUserCondition.length(),
                "Длина userCondition должна совпадать с отправленной длиной");
    }

    private static String selectUserConditionSql(Long experimentId,
                                                 int conditionNumber,
                                                 String userCondition,
                                                 int userConditionLength) {
        return """
                SELECT id
                FROM experiments.exp_object_select_condition
                WHERE exp_id = %s
                  AND condition_no = %s
                  AND user_condition = '%s'
                  AND length(user_condition) = %s
                """.formatted(experimentId, conditionNumber, userCondition, userConditionLength);
    }

    private static String selectExperimentByIdSql(Long experimentId) {
        return """
                SELECT *
                FROM experiments.experiment
                WHERE id = %s
                """.formatted(experimentId);
    }

    private static String fixedLengthUserCondition(int targetLength, String scenarioCode) {
        String prefix = "EXPLAB_2559_" + scenarioCode + "_USER_CONDITION_";
        if (targetLength <= prefix.length()) {
            throw new IllegalArgumentException("Целевая длина userCondition должна быть больше длины префикса");
        }
        return prefix + "X".repeat(targetLength - prefix.length());
    }
}
