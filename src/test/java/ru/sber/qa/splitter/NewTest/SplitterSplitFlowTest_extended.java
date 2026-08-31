package ru.sber.qa.splitter.NewTest;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import request.splitter.SplitterTestDataFactory;
import util.support.SplitterVersionProvider;
import ru.sber.qa.allure.CriticalRegression;


import static request.splitter.SplitterTestDataFactory.*;
import static util.SplitterAssertions.*;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@AnyConfigLoadMode
public class SplitterSplitFlowTest_extended extends AbstractNewSplitterFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("SPL-01. Базовый happy path")
    void splitShouldReturnMainAndAllForMatchedObject() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.baseValidConfig(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем базовый конфиг", flow -> shouldBe200(loadConfig(config)))
                .step("Проверяем split для подходящего объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldContainMainAndAll(response, MATCHED_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("SPL-02. Объект не подходит под условие")
    void splitShouldReturnEmptyResultsForUnmatchedObject() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.baseValidConfig(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForUnmatchedObject();

        getFlowWithRest()
                .step("Загружаем базовый конфиг", flow -> shouldBe200(loadConfig(config)))
                .step("Выполняем split для объекта, который не подходит под условие", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldHaveEmptyObjectResults(response, UNMATCHED_OBJECT_ID);
                })
                .run();
    }

    @Test
    @Disabled("Exploratory only: REQUEST_PARAMS-конфиги конфликтуют с текущим режимом predcalc на стенде, сценарий вынесен из contract-набора")
    @DisplayName("SPL-03. REQUEST_PARAMS-условие применяется ко всем объектам")
    void requestParamsConditionShouldMatchAllObjects() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.requestParamsConfig(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForTwoObjectsWithAgeAndSegment();

        getFlowWithRest()
                .step("Загружаем конфиг с REQUEST_PARAMS правилом", flow -> shouldBe200(loadConfig(config)))
                .step("Проверяем, что правило сработало на оба объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldContainMainAndAll(response, MATCHED_OBJECT_ID);
                    shouldContainMainAndAll(response, SECOND_OBJECT_ID);
                })
                .run();
    }

    @Test
    @Disabled("Exploratory only: сценарий зависит от REQUEST_PARAMS и шумит на текущем стенде, временно исключен из contract-набора")
    @DisplayName("SPL-04. OR-блоки objectSelectConditions выбирают два разных объекта")
    void orRulesShouldSelectTwoDifferentObjects() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.andOrConfig(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForTwoObjectsWithAgeAndSegment();

        getFlowWithRest()
                .step("Загружаем конфиг с OR-блоками", flow -> shouldBe200(loadConfig(config)))
                .step("Проверяем, что оба объекта попали под разные ветки OR", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldContainMainAndAll(response, MATCHED_OBJECT_ID);
                    shouldContainMainAndAll(response, SECOND_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("SPL-06. MAIN выбирается по приоритету actionType")
    void splitShouldSelectMainByPriority() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.priorityConfig(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем конфиг с несколькими экспериментами", flow -> shouldBe200(loadConfig(config)))
                .step("Проверяем выбор MAIN по приоритету", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldHaveMainExpId(response, MATCHED_OBJECT_ID, 603L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("SPL-07. При равном приоритете выбирается минимальный expId")
    void splitShouldSelectMainByMinExpIdWhenPriorityEqual() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.tieConfig(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем конфиг с одинаковым приоритетом", flow -> shouldBe200(loadConfig(config)))
                .step("Проверяем tie-break по expId", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldHaveMainExpId(response, MATCHED_OBJECT_ID, 701L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("SPL-08. Некорректный actionType не участвует в выборе MAIN")
    void invalidActionShouldNotParticipateInMainSelection() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.invalidActionConfig(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем конфиг с невалидным actionType", flow -> shouldBe200(loadConfig(config)))
                .step("Проверяем, что в MAIN выбран валидный эксперимент", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldHaveMainExpId(response, MATCHED_OBJECT_ID, 802L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("SPL-09. Результат ALL содержит все связанные эксперименты")
    void allRuleShouldContainAllLinkedExperiments() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.allRuleConfig(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем конфиг с двумя связанными экспериментами", flow -> shouldBe200(loadConfig(config)))
                .step("Проверяем наполнение ALL", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldHaveMainExpId(response, MATCHED_OBJECT_ID, 402L);
                    response.should(
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '" + MATCHED_OBJECT_ID + "' }" +
                                            ".objectResults.find { it.ruleCode == 'ALL' }.resultExps.size() == 2"
                            )
                    );
                })
                .run();
    }
@Disabled("тест требует доработки")
    @Test
    @DisplayName("SPL-12. На текущем стенде actionType=2 возвращает filtered=false")
    void actionType2ShouldReturnFilteredFalseOnCurrentStand() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.actionType2Config(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем конфиг с actionType=2", flow -> shouldBe200(loadConfig(config)))
                .step("Проверяем текущее поведение стенда", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldHaveFilteredValue(response, MATCHED_OBJECT_ID, "false");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("SPL-13. На текущем стенде actionType=4 возвращает filtered=false")
    void actionType4ShouldReturnFilteredFalseOnCurrentStand() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.actionType4Config(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем конфиг с actionType=4", flow -> shouldBe200(loadConfig(config)))
                .step("Проверяем текущее поведение стенда", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(splitRequest));
                    shouldHaveConfigVersion(response, version);
                    shouldHaveFilteredValue(response, MATCHED_OBJECT_ID, "false");
                    shouldHaveMainExpId(response, MATCHED_OBJECT_ID, 1301L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("SPL-17. Невалидный запрос возвращает 400 Bad Request")
    void invalidSplitRequestShouldReturnBadRequest() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.baseValidConfig(version);
        SplitRequestDto invalidRequest = SplitterTestDataFactory.invalidSplitRequestWithoutRequestId();

        getFlowWithRest()
                .step("Загружаем валидный конфиг", flow -> shouldBe200(loadConfig(config)))
                .step("Отправляем невалидный split без requestId", flow -> shouldBeBadRequestError(split(invalidRequest)))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("SPL-18. Пустой массив объектов возвращает пустой результат")
    void emptyObjectsShouldReturnEmptySplittingResults() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = SplitterTestDataFactory.baseValidConfig(version);
        SplitRequestDto splitRequest = SplitterTestDataFactory.emptyObjectsRequest();

        getFlowWithRest()
                .step("Загружаем валидный конфиг", flow -> shouldBe200(loadConfig(config)))
                .step("Отправляем split с пустым массивом объектов", flow ->
                        shouldBe200(split(splitRequest)).should(
                                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == " + version),
                                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 0")
                        ))
                .run();
    }

    @Disabled("Требует полностью чистого стенда без загруженной конфигурации")
    @Test
    @DisplayName("SPL-16. Без конфига должен возвращаться NO_SPLIT_CONFIG")
    void noConfigCaseIsEnvironmentDependent() {
    }

    private ValidatableResponseWrapper loadConfig(LoadConfigRequestDto request) {
        return getFlowWithRest().flow().restCustomSteps().splitterSteps().loadConfig(request);
    }

    private ValidatableResponseWrapper split(SplitRequestDto request) {
        return getFlowWithRest().flow().restCustomSteps().splitterSteps().split(request);
    }


}
