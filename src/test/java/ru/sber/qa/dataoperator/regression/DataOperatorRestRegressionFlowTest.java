package ru.sber.qa.dataoperator.regression;


import ru.sber.qa.allure.Regression;
import config.environment.EnvironmentConfigWithRest;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.dataoperator.v2.DataOperatorV2TestDataFactory;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.Map;

import static util.dataoperator.DataOperatorV2Assertions.firstObjectId;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveDictionaryContract;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveObjectIdsForAllFoundObjects;
import static util.dataoperator.DataOperatorV2Assertions.shouldHavePageContentAtMost;
import static util.dataoperator.DataOperatorV2Assertions.shouldHaveSplittingObjectsEnvelope;
import static util.dataoperator.DataOperatorV2Assertions.shouldNotHaveObjectIdsKey;
import static util.dataoperator.DataOperatorV2Assertions.shouldPlaceObjectIdsInsideResultStatistics;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("data-operator-regression")
@Regression
public class DataOperatorRestRegressionFlowTest extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("DATA-OP-REG-01. splitting-objects сохраняет базовый контракт и пагинацию")
    void splittingObjectsBaseContractShouldWork() {
        getFlowWithRest()
                .step("Запрашиваем объекты по пустому rules", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequestWithoutReturnIds(0, 2));
                    shouldHaveSplittingObjectsEnvelope(response);
                    shouldHavePageContentAtMost(response, 2);
                    shouldNotHaveObjectIdsKey(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DATA-OP-REG-02. splitting-objects возвращает полный objectIds при returnIds=true")
    void splittingObjectsReturnIdsContractShouldWork() {
        getFlowWithRest()
                .step("Запрашиваем полный список идентификаторов вместе с первой страницей", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequest(true, 0, 1, false));
                    shouldPlaceObjectIdsInsideResultStatistics(response);
                    shouldHaveObjectIdsForAllFoundObjects(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DATA-OP-REG-03. so-field-values-dicts возвращает DTO справочника")
    void fieldValuesDictionaryContractShouldWork() {
        getFlowWithRest()
                .step("Запрашиваем справочник моделей", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSoFieldValuesDictStatusOk(
                                    DataOperatorV2TestDataFactory.modelDictionaryRequest());
                    shouldHaveDictionaryContract(response);
                })
                .run();
    }

    @Test
    @DisplayName("DATA-OP-REG-04. GET object/{splittingPoint}/{id} возвращает сохраненный объект")
    void getObjectByIdShouldReturnExistingObject() {
        getFlowWithRest()
                .step("Получаем id через splitting-objects и читаем объект напрямую", flow -> {
                    ValidatableResponseWrapper registryResponse = flow.restCustomSteps().dataOperatorV2Steps()
                            .getSplittingObjectsStatusOk(
                                    DataOperatorV2TestDataFactory.allObjectsRequest(true, 0, 1, false));
                    String objectId = firstObjectId(registryResponse);

                    ValidatableResponseWrapper objectResponse = flow.restCustomSteps().dataOperatorV2Steps()
                            .getObjectByIdStatusOk(DataOperatorV2TestDataFactory.DEFAULT_SPLITTING_POINT, objectId);
                    Map<?, ?> object = objectResponse.toJsonPath().getMap("");
                    Assertions.assertNotNull(object, "Ответ объекта не должен быть null");
                    Assertions.assertFalse(object.isEmpty(), "Сохраненный объект не должен быть пустым");
                })
                .run();
    }
}
