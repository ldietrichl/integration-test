package ru.sber.qa.experiments.v2.registry;

import config.environment.special.EnvironmentConfigWithRestV2;
import dto.enums.OperatorCodeExpression;
import dto.enums.SortsDirections;
import dto.experiments.v2.ExperimentV2PostRequestDtoBuilder;
import dto.experiments.v2.registry.ExperimentsV2RegistryPostRequestDto;
import dto.experiments.v2.registry.ExperimentsV2RegistryPostRequestDtoBuilder;
import feeders.ExperimentsFeeder;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Link;
import io.qameta.allure.Owner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.allure.CriticalRegression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static io.perfeccionista.framework.datasource.Stash.stash;
import static io.qameta.allure.Allure.step;
import static util.RandomCaseSwitcher.randomToggleCase;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRestV2.class)
@Link("https://confluence.sberbank.ru/pages/viewpage.action?pageId=20987412547")
@DisplayName("Набор API тестов для проверки реестра экспериментов '/api/v2/experiments/registry'")
public class ExperimentsV2RegistryPostTest extends Flows {

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("1 Тест получения реестра экспериментов запрос по умолчанию")
    void response200DefaultTest() {

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().createOrChangeExperimentV2();

        ExperimentsV2RegistryPostRequestDto request
                = ExperimentsV2RegistryPostRequestDtoBuilder.buildDtoDefault();

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().experimentsV2RegistrySteps()
                .getExperimentsRegistryStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 10")
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll { it.id == %s }.size() == 1"
                                        .formatted(stash().getString("experimentV2Id")))
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll { it.name == '%s' }.size() == 1"
                                        .formatted(stash().getString("experimentV2Name")))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_registry_response_200_schema.json")
                );
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("2 Тест получения реестра экспериментов запрос по умолчанию, все поля")
    void response200DefaultAllFieldsTest() {

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().createOrChangeExperimentV2();

        ExperimentsV2RegistryPostRequestDto request
                = ExperimentsV2RegistryPostRequestDtoBuilder.buildDtoDefaultWithFiltersAndSorts();

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().experimentsV2RegistrySteps()
                .getExperimentsRegistryStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 10")
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll { it.id == %s }.size() == 1"
                                        .formatted(stash().getString("experimentV2Id")))
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll { it.name == '%s' }.size() == 1"
                                        .formatted(stash().getString("experimentV2Name")))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_registry_response_200_schema.json")
                );
    }

    @CriticalRegression
    @Test
    @Owner("NAlekZenkin")
    @DisplayName("3 Тест получения реестра экспериментов запрос по умолчанию с фильтром LIKE и NOT_LIKE по имени")
    void response200DefaultWithFilterTest() {

        // Задаем нестандартное имя для одного из экспериментов
        String addExpName = "ExcludedAQAName_" + ExperimentsFeeder.generateAqaMalilId();

        // Создаем эксперимент с нестандартным именем для проверки отсутствия его в результатах,
        // после фильтрации по NOT_LIKE
        step("Создание эксперимента с именем '%s', для проверки работы фильтрации".formatted(addExpName), () ->
                getFlowWithRest().flow().restCustomSteps().experimentsV2Steps()
                        .createOrChangeExperimentV2(
                                ExperimentV2PostRequestDtoBuilder
                                        .buildDefaultExperimentV2PostRequestDto().toBuilder()
                                        .name(addExpName)
                                        .build()));

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().createOrChangeExperimentV2();

        ExperimentsV2RegistryPostRequestDto request
                = ExperimentsV2RegistryPostRequestDtoBuilder.buildDtoDefaultWithFilters()
                .toBuilder()
                .filters(List.of(List.of(
                        ExperimentsV2RegistryPostRequestDto.Filters.builder()
                                .paramCode("name")
                                .operatorCode(OperatorCodeExpression.LIKE)
                                .paramValues(List.of("AQA"))
                                .build(),
                        ExperimentsV2RegistryPostRequestDto.Filters.builder()
                                .paramCode("name")
                                .operatorCode(OperatorCodeExpression.NOT_LIKE)
                                .paramValues(List.of("ExcludedAQAName_"))
                                .build())))
                .build();

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps()
                .experimentsV2RegistrySteps()
                .getExperimentsRegistryStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll { it.name.startsWith('AQA') }.size() == 10")
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll { it.id == %s }.size() == 1"
                                        .formatted(stash().getString("experimentV2Id")))
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll { it.name == '%s' }.size() == 1"
                                        .formatted(stash().getString("experimentV2Name")))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_registry_response_200_schema.json")
                );
    }

    @CriticalRegression
    @ParameterizedTest(name = "{index}) sortingDirection={0}")
    @MethodSource("sortingDirections")
    @Owner("NAlekZenkin")
    @DisplayName("4 Тест получения реестра экспериментов запрос по умолчанию с сортировкой ASC/DESC по id")
    void response200DefaultWithSortTest(SortsDirections sortingDirection) {

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().createOrChangeExperimentV2();

        ExperimentsV2RegistryPostRequestDto request
                = ExperimentsV2RegistryPostRequestDtoBuilder.buildDtoDefaultWithSorts().toBuilder()
                .size(100)
                .sorts(
                        List.of(ExperimentsV2RegistryPostRequestDtoBuilder.sortsDefault().toBuilder()
                                .direction(sortingDirection)
                                .build()))
                .build();

        ValidatableResponseWrapper responseWrapper = getFlowWithRest().flow().restCustomSteps().experimentsV2Steps()
                .experimentsV2RegistrySteps()
                .getExperimentsRegistryStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.size() == 100")
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_registry_response_200_schema.json")
                );

        List<Integer> idsResponse = responseWrapper.toJsonPath().getList("content.id", Integer.class);
        List<Integer> idsToValidation = new ArrayList<>(idsResponse);

        if (sortingDirection == SortsDirections.DESC) {
            responseWrapper.should(
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                            "content.findAll { it.id == %s }.size() == 1"
                                    .formatted(stash().getString("experimentV2Id")))
                    , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                            "content.findAll { it.name == '%s' }.size() == 1"
                                    .formatted(stash().getString("experimentV2Name"))));

            idsToValidation.sort(Collections.reverseOrder());
        } else {
            responseWrapper.should(
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                            "content.findAll { it.id == %s }.size() == 0"
                                    .formatted(stash().getString("experimentV2Id")))
                    , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                            "content.findAll { it.name == '%s' }.size() == 0"
                                    .formatted(stash().getString("experimentV2Name"))));

            idsToValidation.sort(Integer::compare);
        }

        Assertions.assertEquals(
                idsResponse
                , idsToValidation
                , "Полученный реестр экспериментов должен быть отсортирован по '%s' в поле id"
                        .formatted(sortingDirection.getValue()));
    }

    @ParameterizedTest(name = "{index}) sortingDirection={0}")
    @MethodSource("sortingDirections")
    @Owner("NAlekZenkin")
    @DisplayName("5 Тест получения реестра экспериментов запрос по умолчанию с сортировкой ASC/DESC по id,"
            + "проверка на дубликаты")
    void response200DefaultWithSortDuplicateTest(SortsDirections sortingDirection) {

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().createOrChangeExperimentV2();

        ExperimentsV2RegistryPostRequestDto request
                = ExperimentsV2RegistryPostRequestDtoBuilder.buildDtoDefaultWithSorts().toBuilder()
                .size(100)
                .sorts(
                        List.of(ExperimentsV2RegistryPostRequestDtoBuilder.sortsDefault().toBuilder()
                                .direction(sortingDirection)
                                .build()))
                .build();

        ValidatableResponseWrapper responseWrapper = getFlowWithRest().flow().restCustomSteps().experimentsV2Steps()
                .experimentsV2RegistrySteps()
                .getExperimentsRegistryStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.size() == 100")
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_registry_response_200_schema.json")
                );

        List<Integer> idsResponse = responseWrapper.toJsonPath().getList("content.id", Integer.class);
        Set<Integer> idsUniqueToValidation = new HashSet<>(idsResponse);
        Set<Integer> idsDuplicates = new HashSet<>(idsResponse);
        idsDuplicates.removeAll(idsUniqueToValidation);

        Assertions.assertEquals(
                idsResponse.size()
                , idsUniqueToValidation.size()
                , ("Полученный реестр экспериментов должен не содержать дубликатов в поле id: '%s', "
                        + "оригинальный список: '%s', список уникальных значений: '%s'")
                        .formatted(idsUniqueToValidation, idsResponse, idsDuplicates));
    }

    @ParameterizedTest(name = "{index}) sortingDirection={0}")
    @MethodSource("sortingDirections")
    @Owner("NAlekZenkin")
    @DisplayName("6 Тест получения реестра экспериментов запрос по умолчанию с сортировкой ASC/DESC по id,"
            + " проверка отбора только версии 5")
    void response200DefaultWithSortVersion5Test(SortsDirections sortingDirection) {

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().createOrChangeExperimentV2();

        ExperimentsV2RegistryPostRequestDto request
                = ExperimentsV2RegistryPostRequestDtoBuilder.buildDtoDefaultWithSorts().toBuilder()
                .size(100)
                .sorts(
                        List.of(ExperimentsV2RegistryPostRequestDtoBuilder.sortsDefault().toBuilder()
                                .direction(sortingDirection)
                                .build()))
                .build();

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps()
                .experimentsV2RegistrySteps()
                .getExperimentsRegistryStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.size() == 100")
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.every { it.version == 5 }")
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_registry_response_200_schema.json")
                );
    }

    static List<SortsDirections> sortingDirections() {
        return List.of(
                SortsDirections.DESC
                , SortsDirections.ASC
        );
    }

    @ParameterizedTest(name = "{index}) stashString={0}")
    @MethodSource("searchStrings")
    @Owner("NAlekZenkin")
    @DisplayName("7 Тест получения реестра экспериментов запрос по умолчанию со строкой поиска и игнорированием"
            + "регистра, поиск по строкам: id, name, hypothesisDesc, experimentsGroup0, salt, creator")
    void response200DefaultSearchStringTest(String stashString) {

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().createOrChangeExperimentV2();
        String id = stash().getString("experimentV2Id");
        String searchString = stash().getString(stashString);

        if (!id.equals(searchString)) {
            searchString = randomToggleCase(searchString.substring(searchString.lastIndexOf("_") + 1));
        }

        ExperimentsV2RegistryPostRequestDto request
                = ExperimentsV2RegistryPostRequestDtoBuilder.buildDtoDefault()
                .toBuilder()
                .search(searchString)
                .build();

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().experimentsV2RegistrySteps()
                .getExperimentsRegistryStatusOk(request)
                .should(
                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 1")
                        , RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                "content.findAll { it.id == %s }.size() == 1".formatted(id))
                        , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                "schemes/experiments/experiments_registry_response_200_schema.json")
                );
    }

    static List<String> searchStrings() {
        return List.of(
                "experimentV2Id"
                , "experimentV2Name"
                , "experimentV2HypothesisDesc"
                , "experimentV2ExperimentsGroup0"
                , "experimentV2Salt"
                , "experimentV2Creator"
        );
    }

    @ParameterizedTest(name = "{index}) sortField={0}")
    @MethodSource("sortField")
    @Owner("NAlekZenkin")
    @DisplayName("8 Тест получения реестра экспериментов запрос с сортировкой ASC/DESC по всем полям доступным"
            + " для сортировки")
    void response200SortingAllFieldsTest(String sortField) {

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().experimentsV2RegistrySteps()
                .checkExperimentsRegistrySort(sortField, SortsDirections.DESC);

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().experimentsV2RegistrySteps()
                .checkExperimentsRegistrySort(sortField, SortsDirections.ASC);
    }

    static List<String> sortField() {
        return List.of(
                "id"
                , "name"
                , "hypothesis"
                , "salt"
                , "creator"
                , "version"
                , "startDt"
                , "endDt"
        );
    }

    @ParameterizedTest(name = "{index}) sortField1={0}, sortField2={1}")
    @MethodSource("sortFieldPair")
    @Owner("NAlekZenkin")
    @DisplayName("9 Тест получения реестра экспериментов запрос с сортировкой ASC/DESC по всем полям доступным"
            + " для сортировки, два условия сортировки")
    void response200SortingAllFieldsMultipleSortsConditionsTest(String sortField1, String sortField2) {

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().experimentsV2RegistrySteps()
                .checkExperimentsRegistryPairSort(sortField1, sortField2, SortsDirections.DESC);

        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().experimentsV2RegistrySteps()
                .checkExperimentsRegistryPairSort(sortField1, sortField2, SortsDirections.ASC);
    }

    static Stream<Arguments> sortFieldPair() {
        String[] fields = {
                "id"
                , "name"
                , "hypothesis"
                , "salt"
                , "creator"
                , "version"
                , "startDt"
                , "endDt"
        };

        return Stream.of(fields)
                .flatMap(x ->
                        Stream.of(fields)
                                .filter(y -> !x.equals(y))
                                .map(y -> Arguments.of(x, y)));
    }
}
