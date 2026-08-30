package ru.sber.qa.experiments;

import config.environment.EnvironmentConfigWithRest;
import dto.enums.Boolean;
import dto.enums.ExperimentsStatusesV1;
import dto.experiments.v1.ExperimentsV1GetRequestDto;
import dto.experiments.v1.ExperimentsV1GetRequestDtoBuilder;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Owner;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.allure.CriticalRegression;

import java.util.List;
import java.util.stream.Stream;

import static io.perfeccionista.framework.datasource.Stash.stash;
import static io.qameta.allure.Allure.step;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@DisplayName("Набор API тестов для проверки поиска экспериментов в '/api/v1/experiments'")
public class ExperimentsV1GetTest extends Flows {
    private static final String BAD_REQUEST_MESSAGE = "Некорректный запрос";

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("1 Тест получения списка экспериментов")
    void response200DefaultTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps().createDefaultExperiment();

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(ExperimentsV1GetRequestDtoBuilder.buildDtoDefault())
                .should(
                        RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 10")
                        , RestMatchers.haveBodyJsonValueEqualTo("content[0].id", stash().getString("experimentId"))
                        , RestMatchers.haveBodyJsonValueEqualTo("content[0].name", stash().getString("experimentName"))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("2 Тест получения списка экспериментов по имени, неточное совпадение короткого имени")
    void response200NameShortTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .createTwoExperimentWithSimilarNames();

        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoDefault()
                        .toBuilder()
                        .name(stash().getString("experimentName1"))
                        .build());

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 2")
                        , RestMatchers.haveBodyJsonValueEqualTo(
                                "content.find{it.id == %s}.id".formatted(stash().getString("experimentId1"))
                                , stash().getString("experimentId1"))
                        , RestMatchers.haveBodyJsonValueEqualTo(
                                "content.find{it.id == %s}.name".formatted(stash().getString("experimentId1"))
                                , stash().getString("experimentName1"))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("3 Тест получения списка экспериментов по имени, неточное совпадение длинного имени")
    void response200NameLongTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps().createTwoExperimentWithSimilarNames();

        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoDefault()
                        .toBuilder()
                        .name(stash().getString("experimentName2"))
                        .build());

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParameters(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 1")
                        , RestMatchers.haveBodyJsonValueEqualTo(
                                "content.find{it.id == %s}.id".formatted(stash().getString("experimentId2"))
                                , stash().getString("experimentId2"))
                        , RestMatchers.haveBodyJsonValueEqualTo(
                                "content.find{it.id == %s}.name".formatted(stash().getString("experimentId2"))
                                , stash().getString("experimentName2"))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("4 Тест получения списка экспериментов по имени, точное совпадение короткого имени")
    void response200ExactNameShotrTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps().createTwoExperimentWithSimilarNames();

        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoDefault()
                        .toBuilder()
                        .name(stash().getString("experimentName1"))
                        .exact(Boolean.TRUE)
                        .build());

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 1")
                        , RestMatchers.haveBodyJsonValueEqualTo("content[0].id", stash().getString("experimentId1"))
                        , RestMatchers.haveBodyJsonValueEqualTo("content[0].name", stash().getString("experimentName1"))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("5 Тест получения списка экспериментов по имени, точное совпадение длинного имени")
    void response200ExactNameLongTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps().createTwoExperimentWithSimilarNames();

        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoDefault()
                        .toBuilder()
                        .name(stash().getString("experimentName2"))
                        .exact(Boolean.TRUE)
                        .build());

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 1")
                        , RestMatchers.haveBodyJsonValueEqualTo("content[0].id", stash().getString("experimentId2"))
                        , RestMatchers.haveBodyJsonValueEqualTo("content[0].name", stash().getString("experimentName2"))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("6 Тест получения списка из одного эксперимента")
    void response200OneTest() {
        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoDefault()
                        .toBuilder()
                        .page("1")
                        .size("1")
                        .build()
        );

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 1")
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("7 Тест получения списка из 3 экспериментов с пагинацией на второй странице (смещение 1)")
    void response200PaginationTest() {
        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoDefault()
                        .toBuilder()
                        .page("1")
                        .size("3")
                        .build()
        );

        ValidatableResponseWrapper response = getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(request);

        // Создаём эксперименты если страниц меньше двух, необходимо создать экспериментов size x 2 при page 1
        if (response.toJsonPath().getInt("totalPages") < 1) {
            response = step("Если недостаточное количество страниц, формируем нужное количество экспериментов", () -> {
                int experiments = 6;
                while (experiments > 0) {
                    getFlowWithRest().flow().restCustomSteps().experimentsV1Steps().createDefaultExperiment();
                    experiments--;
                }

                return getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                        .findExperimentsWithCustomParametersStatusOk(request);
            });
        }

        response.should(
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 3")
                , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                        "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @ParameterizedTest(name = "{index}) status={0}")
    @MethodSource("correctStatuses")
    @Owner("NAlekZenkin")
    @DisplayName("8 Тест получения списка экспериментов по статусу DRAFT и DRAFT, DRAFT")
    void response200OneStatusTest(List<ExperimentsStatusesV1> statuses) {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps().createTwoExperimentWithSimilarNames();

        getFlowWithRest()
                .step(("Переводим статус второго эксперимента с id: '%s' до AGREED,"
                                + " что бы проконтролировать непопадание AGREED в выборку по фильтру DRAFT")
                                .formatted(stash().getString("experimentId1"))
                        , flow -> flow.restCustomSteps().experimentsV1Steps()
                                .idStatusSteps()
                                .conductExperimentThroughStatuses(
                                        stash().getString("experimentId1")
                                        , List.of(
                                                ExperimentsStatusesV1.DRAFT
                                                , ExperimentsStatusesV1.AGREEMENT
                                                , ExperimentsStatusesV1.AGREED)
                                        , List.of(
                                                "Черновик"
                                                , "На согласовании"
                                                , "Согласован")
                                ))
                .run();

        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoDefault()
                        .toBuilder()
                        .page("0")
                        .size("100")
                        .statuses(statuses)
                        .build());

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 100")
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll{it}.every{ it.statusCode == '%s' }"
                                        .formatted(ExperimentsStatusesV1.DRAFT.name()))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    static List<List<ExperimentsStatusesV1>> correctStatuses() {
        return Stream.of(
                List.of(ExperimentsStatusesV1.DRAFT)
                , List.of(ExperimentsStatusesV1.DRAFT, ExperimentsStatusesV1.DRAFT)
        ).toList();
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("9 Тест получения списка экспериментов по статусам DRAFT, AGREED")
    void response200ManyStatusesTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps().createTwoExperimentWithSimilarNames();

        getFlowWithRest()
                .step(("Проводим статус второго эксперимента с id: '%s' до AGREED,"
                                + " что бы проконтролировать непопадание AGREED в выборку по фильтру DRAFT")
                                .formatted(stash().getString("experimentId1"))
                        , flow -> flow.restCustomSteps().experimentsV1Steps()
                                .idStatusSteps()
                                .conductExperimentThroughStatuses(
                                        stash().getString("experimentId1")
                                        , List.of(
                                                ExperimentsStatusesV1.DRAFT
                                                , ExperimentsStatusesV1.AGREEMENT
                                                , ExperimentsStatusesV1.AGREED)
                                        , List.of(
                                                "Черновик"
                                                , "На согласовании"
                                                , "Согласован")
                                ))
                .run();

        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoDefault()
                        .toBuilder()
                        .page("0")
                        .size("100")
                        .statuses(List.of(
                                ExperimentsStatusesV1.DRAFT
                                , ExperimentsStatusesV1.AGREED))
                        .build());

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 100")
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll{it}.every{ it.statusCode == '%s' || it.statusCode == '%s' }"
                                        .formatted(
                                                ExperimentsStatusesV1.DRAFT.getValue()
                                                , ExperimentsStatusesV1.AGREED.getValue()))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("10 Тест получения списка экспериментов по пустому имени и точному совпадению")
    void response200EmptyNameTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps().createDefaultExperiment();

        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty()
                        .toBuilder()
                        .name("")
                        .exact(Boolean.TRUE)
                        .build());

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 10")
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.every{ it.name.trim().size() > 0 }")
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("11 Тест получения списка экспериментов по соли")
    void response200SaltTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps().createDefaultExperiment();

        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty()
                        .toBuilder()
                        .salt(stash().getString("experimentSalt"))
                        .build());

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 1")
                        , RestMatchers.haveBodyJsonValueEqualTo(
                                "content[0].id", stash().getString("experimentId"))
                        , RestMatchers.haveBodyJsonValueEqualTo(
                                "content[0].name", stash().getString("experimentName"))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_200_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("12 Негативный тест невалидное значение page (не число)")
    void response400InvalidPageNotNumberTest() {
        ExperimentsV1GetRequestDto request = step("Формирование кастомных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty()
                        .toBuilder()
                        .page("abc")
                        .build());

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusBadRequest(request)
                .should(
                        RestMatchers.haveBodyJsonValueContains("message", BAD_REQUEST_MESSAGE)
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_400_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("13 Негативный тест невалидное значение size (не число)")
    void response400InvalidSizeNotNumberTest() {
        ExperimentsV1GetRequestDto request = ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty()
                .toBuilder()
                .size("xyz")
                .build();

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusBadRequest(request)
                .should(
                        RestMatchers.haveBodyJsonValueContains("message", BAD_REQUEST_MESSAGE)
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_400_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("14 Негативный тест отрицательное значение page")
    void response400InvalidPageNegativeNumberTest() {
        ExperimentsV1GetRequestDto request = ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty()
                .toBuilder()
                .page("-1")
                .build();

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusBadRequest(request)
                .should(
                        RestMatchers.haveBodyJsonValueContains("message", "Page index must not be less than zero")
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_400_schema.json"));
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("15 Негативный тест отрицательное значение size")
    void response400InvalidSizeNegativeNumberTest() {
        ExperimentsV1GetRequestDto request = ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty()
                .toBuilder()
                .size("-1")
                .build();

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusBadRequest(request)
                .should(
                        RestMatchers.haveBodyJsonValueContains("message", "Page size must not be less than one")
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_400_schema.json"));
    }

    @CriticalRegression
    @ParameterizedTest(name = "{index}) status={0}")
    @MethodSource("incorrectStatuses")
    @Owner("NAlekZenkin")
    @DisplayName("16 Негативный тест невалидные статусы")
    void response400IncorrectStatusTest(ExperimentsStatusesV1 status) {
        ExperimentsV1GetRequestDto request = ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty().toBuilder()
                .statuses(List.of(status))
                .build();

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusBadRequest(request)
                .should(
                        RestMatchers.haveBodyJsonValueContains("message", BAD_REQUEST_MESSAGE)
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_400_schema.json"));
    }

    static List<ExperimentsStatusesV1> incorrectStatuses() {
        return Stream.of(
                ExperimentsStatusesV1.NONEXISTENT_FOR_TEST
                , ExperimentsStatusesV1.INCORRECT_STATUS_FOR_TEST
                , ExperimentsStatusesV1.DRAFT_LOWER_CASE_FOR_TEST
        ).toList();
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("17 Негативный тест невалидное булево значение точного совпадения")
    void response400IncorrectExactTest() {
        ExperimentsV1GetRequestDto request = ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty().toBuilder()
                .exact(Boolean.INCORRECT_EXACT_FOR_TEST)
                .build();

        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusBadRequest(request)
                .should(
                        RestMatchers.haveBodyJsonValueContains("message", BAD_REQUEST_MESSAGE)
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_400_schema.json"));
    }

    @CriticalRegression
    @ParameterizedTest(name = "{index}) request={0}")
    @MethodSource("pageSize")
    @Owner("NAlekZenkin")
    @DisplayName("18 Негативный тест отсутствую обязательные параметры page и size")
    void response400RequiredParamsTest(ExperimentsV1GetRequestDto request) {
        getFlowWithRest().flow().restCustomSteps().experimentsV1Steps()
                .findExperimentsWithCustomParametersStatusBadRequest(request)
                .should(
                        RestMatchers.haveBodyJsonValueContains("message", "Непредвиденная ошибка")
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_get_response_400_schema.json"));
    }

    static List<ExperimentsV1GetRequestDto> pageSize() {
        return Stream.of(
                ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty().toBuilder()
                        .page(null)
                        .size(null)
                        .build()
                , ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty().toBuilder()
                        .page(null)
                        .build()
                , ExperimentsV1GetRequestDtoBuilder.buildDtoEmpty().toBuilder()
                        .size(null)
                        .build()
        ).toList();
    }

}
