package ru.sber.qa.experiments.EXPLAB_2559;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import dto.experiments.v2.ExperimentV2PostRequestDto;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.validation.ValidatableJson;

import java.util.List;
import java.util.stream.Stream;

import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto;
import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultObjectSelectCondition;
import static ru.sber.qa.experiments.EXPLAB_2559.ExperimentV2UserConditionScopeAssumptions.assumeMapperScopeAvailable;

/**
 * EXPLAB-2559.
 * Проверяем, что поле objectSelectConditions[].userCondition сохраняет не только длину,
 * но и исходное содержимое пользовательского условия: кириллицу, переносы строк,
 * спецсимволы, JSON-like и SQL-like текст.
 */
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
@ResourceLock("explab-2559-user-condition")
public class ExperimentV2UserConditionContentFlowTest extends Flows {


    @ParameterizedTest(name = "{0}: userCondition length = {2}")
    @MethodSource("userConditionContentCases")
    @DisplayName("EXPLAB-2559. Создание эксперимента V2 с userCondition разного содержимого")
    void createExperimentV2WithUserConditionContentSuccessTest(String caseName,
                                                               String userCondition,
                                                               int expectedLength) {
        assumeMapperScopeAvailable();

        final Long[] experimentId = new Long[1];
        final boolean[] deleted = {false};

        try {
            getFlowWithDbRest()
                    .step("Создаем эксперимент V2: " + caseName
                            + ", userCondition длиной " + expectedLength + " символов", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .createOrChangeExperimentV2(buildExperimentWithUserCondition(userCondition))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        experimentId[0] = result.getJsonPath().getLong("id");
                        Assertions.assertNotNull(experimentId[0], "В ответе создания эксперимента V2 должен вернуться id");
                    })
                    .step("Получаем созданный эксперимент V2 и проверяем, что userCondition вернулся без изменения содержимого", flow -> {
                        ValidatableJson result = flow.restCustomSteps().experimentsV2Steps()
                                .getExperimentV2ById(experimentId[0])
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK))
                                .toValidatableJson();

                        String actualUserCondition = result.getJsonPath()
                                .getString("objectSelectConditions[0].userCondition");

                        assertUserCondition(actualUserCondition, userCondition, expectedLength,
                                "GET /api/v2/experiments/{id} должен вернуть userCondition без изменения содержимого");
                    })
                    .step("Проверяем в БД, что userCondition сохранен без изменения содержимого", flow ->
                            flow.dbExpLabClient()
                                    .executeSelect(selectUserConditionSql(experimentId[0], 1, userCondition, expectedLength))
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

    private static Stream<Arguments> userConditionContentCases() {
        return Stream.of(
                Arguments.of(
                        "Кириллица",
                        fixedLengthText("EXPLAB_2559_КИРИЛЛИЦА_", "условие отбора клиента по продукту и кампании ", 300),
                        300
                ),
                Arguments.of(
                        "Переносы строк и табуляция",
                        fixedLengthText("EXPLAB_2559_MULTILINE_", "строка_1\nстрока_2\n\tстрока_3 ", 300),
                        300
                ),
                Arguments.of(
                        "JSON-like текст",
                        fixedLengthText("EXPLAB_2559_JSON_LIKE_", "{code:[1,2,3],flag:true}; name=\"condition\"; ", 300),
                        300
                ),
                Arguments.of(
                        "SQL-like текст",
                        fixedLengthText("EXPLAB_2559_SQL_LIKE_", "'; DROP TABLE experiments.exp_object_select_condition; -- ", 300),
                        300
                ),
                Arguments.of(
                        "Unicode BMP символы",
                        fixedLengthText("EXPLAB_2559_UNICODE_", "№ Ё ё — – «условие» <= >= == != ", 300),
                        300
                )
        );
    }

    private static ExperimentV2PostRequestDto buildExperimentWithUserCondition(String userCondition) {
        ExperimentV2PostRequestDto.ObjectSelectCondition condition = buildDefaultObjectSelectCondition().toBuilder()
                .number(1)
                .userCondition(userCondition)
                .build();

        return buildDefaultExperimentV2PostRequestDto().toBuilder()
                .objectSelectConditions(List.of(condition))
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
                """.formatted(experimentId, conditionNumber, sqlEscape(userCondition), userConditionLength);
    }

    private static String selectExperimentByIdSql(Long experimentId) {
        return """
                SELECT *
                FROM experiments.experiment
                WHERE id = %s
                """.formatted(experimentId);
    }

    private static String fixedLengthText(String prefix, String pattern, int targetLength) {
        if (targetLength <= prefix.length()) {
            throw new IllegalArgumentException("Целевая длина userCondition должна быть больше длины префикса");
        }

        StringBuilder value = new StringBuilder(prefix);
        while (value.length() < targetLength) {
            value.append(pattern);
        }
        return value.substring(0, targetLength);
    }

    private static String sqlEscape(String value) {
        return value.replace("'", "''");
    }
}
