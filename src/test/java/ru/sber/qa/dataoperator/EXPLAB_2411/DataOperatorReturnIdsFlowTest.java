package ru.sber.qa.dataoperator.EXPLAB_2411;

import config.environment.EnvironmentConfigWithRest;
import dto.dataoperator.v2.SplittingObjectRuleDto;
import dto.dataoperator.v2.SplittingObjectsRequestDto;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.dataoperator.v2.DataOperatorV2TestDataFactory;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static util.dataoperator.DataOperatorV2Assertions.ruleFromFirstKnownField;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveEmptyResultWithoutOptionalStatistics;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveObjectIdsForAllFoundObjects;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveObjectIdsKey;
import static util.dataoperator.DataOperatorV2Assertions.shouldHavePageContentAtMost;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveResultStatisticsConsistentWithIds;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveSameContentAndBaseStatistics;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveSameObjectIds;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveSplittingObjectsEnvelope;
import static util.dataoperator.DataOperatorV2Assertions.shouldNotHaveObjectIdsKey;
import static util.dataoperator.DataOperatorV2Assertions.shouldPlaceObjectIdsInsideResultStatistics;
import static util.dataoperator.DataOperatorV2Assertions.splittingObjectsDto;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("data-operator-splitting-objects")
public class DataOperatorReturnIdsFlowTest extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2411-FT-01. returnIds=true возвращает полный список id внутри resultStatistics")
    void returnIdsTrueShouldReturnNestedObjectIds() {
        getFlowWithRest()
                .step("Запрашиваем первую страницу объектов с returnIds=true", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequest(true, 0, 1, false));

                    shouldHaveSplittingObjectsEnvelope(response);
                    shouldPlaceObjectIdsInsideResultStatistics(response);
                    shouldHaveObjectIdsForAllFoundObjects(response);
                    shouldHavePageContentAtMost(response, 1);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2411-FT-02. objectIds не зависит от page и size контента")
    void objectIdsShouldBeIndependentFromPagination() {
        getFlowWithRest()
                .step("Сравниваем запросы с разным размером и номером страницы", flow -> {
                    ValidatableResponseWrapper firstPage = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequest(true, 0, 1, false));
                    ValidatableResponseWrapper largerPage = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequest(true, 0, 3, false));

                    shouldHaveSameObjectIds(firstPage, largerPage);
                    shouldHavePageContentAtMost(firstPage, 1);
                    shouldHavePageContentAtMost(largerPage, 3);

                    int totalPages = splittingObjectsDto(firstPage).getTotalPages();
                    if (totalPages > 1) {
                        ValidatableResponseWrapper lastPage = flow.restCustomSteps().dataOperatorV2Steps()
                                .getSplittingObjectsStatusOk(
                                        DataOperatorV2TestDataFactory.allObjectsRequest(true, totalPages - 1, 1, false));
                        shouldHaveSameObjectIds(firstPage, lastPage);
                    }
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2411-FT-03. returnIds=false не добавляет resultStatistics.objectIds")
    void returnIdsFalseShouldOmitObjectIds() {
        getFlowWithRest()
                .step("Запрашиваем объекты с returnIds=false", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(DataOperatorV2TestDataFactory.allObjectsRequest(false));

                    shouldHaveSplittingObjectsEnvelope(response);
                    shouldNotHaveObjectIdsKey(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2411-FT-04. Отсутствующий returnIds сохраняет контракт v2.0.0")
    void omittedReturnIdsShouldPreservePreviousContract() {
        getFlowWithRest()
                .step("Запрашиваем объекты без поля returnIds", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequestWithoutReturnIds(0, 5));

                    shouldHaveSplittingObjectsEnvelope(response);
                    shouldNotHaveObjectIdsKey(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2411-FT-05. Явный returnIds=null не добавляет objectIds")
    void explicitNullReturnIdsShouldOmitObjectIds() {
        getFlowWithRest()
                .step("Запрашиваем объекты с явным JSON null в returnIds", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequestWithExplicitNullReturnIds());

                    shouldHaveSplittingObjectsEnvelope(response);
                    shouldNotHaveObjectIdsKey(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2411-FT-06. returnIds влияет только на objectIds, но не на content и базовую статистику")
    void returnIdsShouldNotChangeExistingResponseData() {
        getFlowWithRest()
                .step("Сравниваем одинаковый запрос с returnIds=true и false", flow -> {
                    SplittingObjectsRequestDto withIdsRequest =
                            DataOperatorV2TestDataFactory.allObjectsRequest(true, 0, 5, false);
                    SplittingObjectsRequestDto withoutIdsRequest = withIdsRequest.toBuilder()
                            .returnIds(false)
                            .build();

                    ValidatableResponseWrapper withIds = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(withIdsRequest);
                    ValidatableResponseWrapper withoutIds = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(withoutIdsRequest);

                    shouldHaveSameContentAndBaseStatistics(withIds, withoutIds);
                    shouldHaveObjectIdsKey(withIds);
                    shouldNotHaveObjectIdsKey(withoutIds);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2411-FT-07. objectIds учитывает rules и остается полным при size=1")
    void filteredObjectIdsShouldRespectRules() {
        getFlowWithRest()
                .step("Получаем параметр существующего объекта и формируем equal-фильтр", flow -> {
                    ValidatableResponseWrapper baseline = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequest(true, 0, 5, false));
                    SplittingObjectRuleDto rule = ruleFromFirstKnownField(baseline);

                    ValidatableResponseWrapper filtered = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.requestWithRule(true, 0, 1, rule));

                    shouldHaveSplittingObjectsEnvelope(filtered);
                    shouldHaveResultStatisticsConsistentWithIds(filtered);
                    shouldHavePageContentAtMost(filtered, 1);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2411-FT-08. Для пустой выборки objectIds и parentObjectsFound не добавляются")
    void emptyResultShouldNotContainOptionalStatistics() {
        getFlowWithRest()
                .step("Формируем пустую выборку на существующей точке MAPPER", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.emptyResultRequest(true));

                    shouldHaveEmptyResultWithoutOptionalStatistics(response);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2411-FT-09. parent=true возвращает id найденных дочерних объектов")
    void parentModeShouldReturnConsistentChildObjectIds() {
        getFlowWithRest()
                .step("Запрашиваем родительские объекты с полным списком id найденных объектов", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequest(true, 0, 5, true));

                    shouldHaveSplittingObjectsEnvelope(response);
                    shouldHaveResultStatisticsConsistentWithIds(response);
                })
                .run();
    }
}
