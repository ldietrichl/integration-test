package ru.sber.qa.experiments.EXPLAB_2559;

import config.environment.special.EnvironmentConfigWithRestV2;
import dto.experiments.v2.ExperimentV2PostRequestDto;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;
import java.util.stream.Stream;

import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto;
import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultObjectSelectCondition;

/**
 * EXPLAB-2559.
 * Негативные проверки userCondition вынесены отдельно от позитивного flow-класса.
 *
 * Важно: проверки на 2001+ символов фиксируют ожидаемое бизнес-ограничение в 2000 символов.
 * Если сервис принимает такие значения, это повод завести дефект/уточнение требований, а не расширять позитивный набор.
 */

@Disabled("Временно отключен -  Трофимов : на бэке тож поставлю с какой-нибудь следующей задачей (там 1 строчка), но в задаче речь идет о проверке фронта")
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRestV2.class)
@ResourceLock("explab-2559-user-condition")
public class ExperimentV2UserConditionValidationFlowTest extends Flows {


    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUserConditionCases")
    @DisplayName("EXPLAB-2559. Негативные проверки userCondition при создании эксперимента V2")
    void createExperimentV2WithInvalidUserConditionValidationErrorTest(String caseName,
                                                                       ExperimentV2PostRequestDto requestBody) {
        getFlowWithRest()
                .step("Отправляем запрос создания эксперимента V2 с невалидным userCondition: " + caseName, flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().experimentsV2Steps()
                            .createOrChangeExperimentV2(requestBody);

                    cleanupIfExperimentWasUnexpectedlyCreated(response);

                    response.should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST))
                            .toValidatableJson()
                            .should(
                                    JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                    JsonMatchers.haveJsonValue("message", TextConditions.equalToText("Некорректный запрос"))
                            );
                })
                .run();
    }

    private static Stream<Arguments> invalidUserConditionCases() {
        return Stream.of(
                Arguments.of(
                        "userCondition отсутствует/null",
                        buildExperimentWithUserCondition(null)
                ),
                Arguments.of(
                        "userCondition больше лимита: 2001 символ",
                        buildExperimentWithUserCondition(fixedLengthUserCondition(2001, "MORE_THAN_LIMIT"))
                ),
                Arguments.of(
                        "userCondition сильно больше лимита: 5000 символов",
                        buildExperimentWithUserCondition(fixedLengthUserCondition(5000, "MUCH_MORE_THAN_LIMIT"))
                )
        );
    }

    private void cleanupIfExperimentWasUnexpectedlyCreated(ValidatableResponseWrapper response) {
        try {
            Long experimentId = response.toJsonPath().getLong("id");
            if (experimentId != null) {
                getFlowWithRest().flow().restCustomSteps().experimentsV2Steps()
                        .deleteExperimentV2ById(experimentId);
            }
        } catch (RuntimeException ignored) {
            // Для негативного сценария id обычно отсутствует. Cleanup нужен только если сервис неожиданно создал эксперимент.
        }
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

    private static String fixedLengthUserCondition(int targetLength, String scenarioCode) {
        String prefix = "EXPLAB_2559_" + scenarioCode + "_USER_CONDITION_";
        if (targetLength <= prefix.length()) {
            throw new IllegalArgumentException("Целевая длина userCondition должна быть больше длины префикса");
        }
        return prefix + "X".repeat(targetLength - prefix.length());
    }
}
