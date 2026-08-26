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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.validation.ValidatableJson;

import java.util.List;

import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentGroups;
import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto;
import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultObjectSelectCondition;

/**
 * EXPLAB-2559.
 * Проверяем, что при создании и изменении эксперимента V2 поле objectSelectConditions[].userCondition
 * принимает и сохраняет значения длиннее 256 символов без обрезки.
 */
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
@ResourceLock("explab-2559-user-condition")
public class ExperimentV2UserConditionLengthFlowTest extends Flows {


    @ParameterizedTest(name = "userCondition length = {0}")
    @ValueSource(ints = {255, 256, 257, 300, 512, 1000, 2000})
    @DisplayName("EXPLAB-2559. Создание эксперимента V2 с разной длиной userCondition")
    void createExperimentV2WithUserConditionBoundaryLengthsSuccessTest(int userConditionLength) {
        final Long[] experimentId = new Long[1];
        final boolean[] deleted = {false};
        String userCondition = fixedLengthUserCondition(userConditionLength, "BOUNDARY");

        try {
            getFlowWithDbRest()
                    .step("Создаем эксперимент V2 с objectSelectConditions[0].userCondition длиной "
                            + userConditionLength + " символов", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .createOrChangeExperimentV2(buildExperimentWithUserCondition(userCondition))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        experimentId[0] = result.getJsonPath().getLong("id");
                        Assertions.assertNotNull(experimentId[0], "В ответе создания эксперимента V2 должен вернуться id");
                    })
                    .step("Получаем созданный эксперимент V2 и проверяем, что userCondition вернулся без обрезки", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .getExperimentV2ById(experimentId[0])
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        String actualUserCondition = result.getJsonPath()
                                .getString("objectSelectConditions[0].userCondition");

                        assertUserCondition(actualUserCondition, userCondition, userConditionLength,
                                "GET /api/v2/experiments/{id} должен вернуть userCondition без обрезки");
                    })
                    .step("Проверяем в БД, что userCondition сохранен полностью в experiments.exp_object_select_condition", flow ->
                            flow.dbExpLabClient()
                                    .executeSelect(selectUserConditionSql(experimentId[0], 1, userCondition, userConditionLength))
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


    @Test
    @DisplayName("EXPLAB-2559. Обновление эксперимента V2 длинным userCondition")
    void updateExperimentV2WithLongUserConditionSuccessTest() {
        final Long[] experimentId = new Long[1];
        final boolean[] deleted = {false};
        String createUserCondition = fixedLengthUserCondition(100, "CREATE");
        String updateUserCondition = fixedLengthUserCondition(512, "UPDATE");

        try {
            getFlowWithDbRest()
                    .step("Создаем эксперимент V2 с коротким userCondition", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .createOrChangeExperimentV2(buildExperimentWithUserCondition(createUserCondition))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        experimentId[0] = result.getJsonPath().getLong("id");
                        Assertions.assertNotNull(experimentId[0], "В ответе создания эксперимента V2 должен вернуться id");
                    })
                    .step("Обновляем созданный эксперимент V2: передаем userCondition длиной 512 символов", flow ->
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

                        assertUserCondition(actualUserCondition, updateUserCondition, 512,
                                "GET /api/v2/experiments/{id} должен вернуть обновленный userCondition без обрезки");
                    })
                    .step("Проверяем в БД, что после обновления сохранено новое длинное значение userCondition", flow ->
                            flow.dbExpLabClient()
                                    .executeSelect(selectUserConditionSql(experimentId[0], 1, updateUserCondition, 512))
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


    @Test
    @DisplayName("EXPLAB-2559. Создание эксперимента V2 с двумя длинными objectSelectConditions[].userCondition")
    void createExperimentV2WithMultipleLongUserConditionsSuccessTest() {
        final Long[] experimentId = new Long[1];
        final boolean[] deleted = {false};
        String firstUserCondition = fixedLengthUserCondition(300, "FIRST");
        String secondUserCondition = fixedLengthUserCondition(512, "SECOND");

        try {
            getFlowWithDbRest()
                    .step("Создаем эксперимент V2 с двумя objectSelectConditions, оба userCondition длиннее 256 символов", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .createOrChangeExperimentV2(buildExperimentWithTwoUserConditions(firstUserCondition, secondUserCondition))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        experimentId[0] = result.getJsonPath().getLong("id");
                        Assertions.assertNotNull(experimentId[0], "В ответе создания эксперимента V2 должен вернуться id");
                    })
                    .step("Получаем созданный эксперимент V2 и проверяем, что оба userCondition вернулись без обрезки", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .getExperimentV2ById(experimentId[0])
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        List<String> actualUserConditions = result.getJsonPath()
                                .getList("objectSelectConditions.userCondition", String.class);

                        Assertions.assertNotNull(actualUserConditions,
                                "GET /api/v2/experiments/{id} должен вернуть массив objectSelectConditions.userCondition");
                        Assertions.assertTrue(actualUserConditions.contains(firstUserCondition),
                                "GET /api/v2/experiments/{id} должен вернуть первый userCondition без обрезки");
                        Assertions.assertTrue(actualUserConditions.contains(secondUserCondition),
                                "GET /api/v2/experiments/{id} должен вернуть второй userCondition без обрезки");
                        Assertions.assertEquals(300, firstUserCondition.length(),
                                "Контрольная длина первого userCondition должна быть 300 символов");
                        Assertions.assertEquals(512, secondUserCondition.length(),
                                "Контрольная длина второго userCondition должна быть 512 символов");
                    })
                    .step("Проверяем в БД, что оба userCondition сохранены полностью", flow ->
                            flow.dbExpLabClient()
                                    .executeSelect("""
                                            SELECT id
                                            FROM experiments.exp_object_select_condition
                                            WHERE exp_id = %s
                                              AND (
                                                  (condition_no = 1 AND user_condition = '%s' AND length(user_condition) = 300)
                                                  OR
                                                  (condition_no = 2 AND user_condition = '%s' AND length(user_condition) = 512)
                                              )
                                            """.formatted(experimentId[0], firstUserCondition, secondUserCondition))
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
