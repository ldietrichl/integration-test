package ru.sber.qa.splitter.NewTest;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;
import ru.sber.qa.allure.CriticalRegression;

import static request.splitter.SplitterPrecalcTestDataFactory.MATCHING_OBJECT_ID;
import static request.splitter.SplitterPrecalcTestDataFactory.changedGuaranteedPositivePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.changedGuaranteedPositiveSplitRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.emptyPrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedNegativePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedNegativeSplitRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedPositiveConfig;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedPositivePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.GUARANTEED_POSITIVE_EXP_ID;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedPositiveSplitRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedPositiveSplitRequestForObjectId;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoaded;
import static util.SplitterPrecalcAssertions.shouldHaveNonEmptySplitEnvelope;
import static util.SplitterPrecalcAssertions.shouldHaveSingleObjectWithEmptyResults;
import static util.SplitterPrecalcAssertions.shouldContainConditionId;
import static util.SplitterPrecalcAssertions.shouldContainExpId;
import static util.SplitterPrecalcAssertions.shouldHaveSingleObjectWithNonEmptyResults;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
public class SplitterPrecalcMandatoryFlowTest extends AbstractNewSplitterFlowTest {

    private static final int SO_CONFIG_VERSION = 1;

    @CriticalRegression
    @Test
    @DisplayName("PC-M-05. guaranteed-positive объект возвращает результат после predcalc")
    void guaranteedPositiveObjectShouldReturnResultAfterPrecalc() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-m-05");
        SplitterPrecalcRequestDto precalcRequest = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto splitRequest = minimalGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем минимальный guaranteed-positive config", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(response);
                })
                .step("Выполняем predcalc для guaranteed-positive объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("Split по объекту из таблицы должен вернуть непустой результат", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-M-06. guaranteed-positive объект стабилен до и после predcalc")
    void guaranteedPositiveObjectShouldStayPositiveBeforeAndAfterPrecalc() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-m-06");
        SplitterPrecalcRequestDto precalcRequest = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto splitRequest = minimalGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем минимальный guaranteed-positive config", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(response);
                })
                .step("До predcalc runtime split уже должен возвращать непустой результат", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .step("Выполняем predcalc для того же объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("После predcalc результат остается непустым", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-M-07. guaranteed-negative объект остается без связей после predcalc")
    void guaranteedNegativeObjectShouldStayEmptyAfterPrecalc() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-m-07");
        SplitterPrecalcRequestDto precalcRequest = minimalGuaranteedNegativePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto splitRequest = minimalGuaranteedNegativeSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем минимальный guaranteed-positive config", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(response);
                })
                .step("Выполняем predcalc для guaranteed-negative объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("Split по non-matching объекту возвращает пустой business-result", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-M-08. guaranteed-negative объект стабилен до и после predcalc")
    void guaranteedNegativeObjectShouldStayNegativeBeforeAndAfterPrecalc() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-m-08");
        SplitterPrecalcRequestDto precalcRequest = minimalGuaranteedNegativePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto splitRequest = minimalGuaranteedNegativeSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем минимальный guaranteed-positive config", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(response);
                })
                .step("До predcalc runtime split возвращает пустой business-result", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .step("Выполняем predcalc для guaranteed-negative объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("После predcalc объект по-прежнему остается без связей", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-AK-01. пустой predcalc очищает таблицу")
    void emptyPrecalcShouldClearTable() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-ak-01");
        SplitterPrecalcRequestDto baselinePrecalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitterPrecalcRequestDto emptyPrecalc = emptyPrecalcRequest(SO_CONFIG_VERSION);
        SplitRequestDto changedSplitRequest = changedGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем config и строим baseline таблицу для объекта B", flow -> {
                    shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config)));
                    shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, baselinePrecalc)), SO_CONFIG_VERSION);
                })
                .step("До очистки таблицы split использует старую precalc-связь для измененного B", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, changedSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .step("Пустой predcalc должен очистить таблицу", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, emptyPrecalc));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("После очистки таблицы измененный B больше не использует старую связь", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, changedSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-AK-02. объект с тем же uniqueConfigurationId не должен замещаться новым")
    void existingObjectShouldNotBeReplacedByChangedObjectWithSameUniqueConfigurationId() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-ak-02");
        SplitterPrecalcRequestDto baselinePrecalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitterPrecalcRequestDto changedPrecalc = changedGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto changedSplitRequest = changedGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем config и строим baseline таблицу для объекта B", flow -> {
                    shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config)));
                    shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, baselinePrecalc)), SO_CONFIG_VERSION);
                })
                .step("Повторно загружаем измененный B с тем же uniqueConfigurationId", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, changedPrecalc));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("Измененный B не должен вытеснить исходную precalc-связь", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, changedSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-AK-03. базовый результат объекта B фиксируется после predcalc")
    void baselineResultForObjectBShouldBeFixedAfterPrecalc() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-ak-03");
        SplitterPrecalcRequestDto baselinePrecalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto baselineSplitRequest = minimalGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Строим таблицу predcalc для объекта B", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, baselinePrecalc));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("Split по объекту B фиксирует baseline-результат", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, baselineSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-AK-04. повторная загрузка измененного B не меняет результат")
    void reloadingChangedObjectBShouldNotChangeResult() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-ak-04");
        SplitterPrecalcRequestDto baselinePrecalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitterPrecalcRequestDto changedPrecalc = changedGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto baselineSplitRequest = minimalGuaranteedPositiveSplitRequest(uniqueConfigurationId);
        SplitRequestDto changedSplitRequest = changedGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем config и строим baseline таблицу", flow -> {
                    shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config)));
                    shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, baselinePrecalc)), SO_CONFIG_VERSION);
                })
                .step("Фиксируем baseline-результат по объекту B", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, baselineSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .step("Повторно загружаем измененный B с тем же uniqueConfigurationId", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, changedPrecalc));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("Результат для измененного B остается тем же", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, changedSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-M-09. повторный predcalc того же объекта с тем же uniqueConfigurationId и теми же данными не меняет результат")
    void repeatedPrecalcForSameObjectShouldStayStable() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-m-09");
        SplitterPrecalcRequestDto firstPrecalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitterPrecalcRequestDto secondPrecalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto splitRequest = minimalGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем guaranteed-positive config", flow ->
                        shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Первый predcalc для объекта", flow ->
                        shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, firstPrecalc)), SO_CONFIG_VERSION))
                .step("Повторный predcalc того же объекта", flow ->
                        shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, secondPrecalc)), SO_CONFIG_VERSION))
                .step("Split остается положительным и после повторного predcalc", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-M-10. тот же бизнес-объект с новым uniqueConfigurationId обрабатывается как новая запись")
    void sameBusinessObjectWithNewUniqueIdShouldBeProcessedAsNewRecord() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String oldUniqueConfigurationId = uniqueId("pc-m-10-old");
        String newUniqueConfigurationId = uniqueId("pc-m-10-new");
        String secondObjectId = "77777777-7777-7777-7777-777777777777";

        SplitterPrecalcRequestDto firstPrecalc = minimalGuaranteedPositivePrecalcRequest(oldUniqueConfigurationId, SO_CONFIG_VERSION);
        SplitterPrecalcRequestDto secondPrecalc = minimalGuaranteedPositivePrecalcRequest(newUniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto oldSplitRequest = minimalGuaranteedPositiveSplitRequest(oldUniqueConfigurationId);
        SplitRequestDto newSplitRequest = minimalGuaranteedPositiveSplitRequestForObjectId(newUniqueConfigurationId, secondObjectId);

        getFlowWithRest()
                .step("Загружаем guaranteed-positive config", flow ->
                        shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Строим predcalc для первой записи", flow ->
                        shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, firstPrecalc)), SO_CONFIG_VERSION))
                .step("Строим predcalc для того же бизнес-объекта, но с новым uniqueConfigurationId", flow ->
                        shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, secondPrecalc)), SO_CONFIG_VERSION))
                .step("Старая запись остается рабочей", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, oldSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .step("Новая запись тоже используется как самостоятельная", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, newSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, secondObjectId);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-M-11. guaranteed-positive predcalc сохраняет expId и conditionId в результате split")
    void guaranteedPositivePrecalcShouldPreserveExpectedExpAndCondition() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-m-11");
        SplitterPrecalcRequestDto precalcRequest = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto splitRequest = minimalGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем guaranteed-positive config", flow ->
                        shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Выполняем predcalc для guaranteed-positive объекта", flow ->
                        shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, precalcRequest)), SO_CONFIG_VERSION))
                .step("Split содержит ожидаемые expId и conditionId", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                    shouldContainExpId(response, MATCHING_OBJECT_ID, GUARANTEED_POSITIVE_EXP_ID);
                    shouldContainConditionId(response, MATCHING_OBJECT_ID, 1L);
                })
                .run();
    }

    private ValidatableResponseWrapper loadConfig(FlowWithRest flow, LoadConfigRequestDto request) {
        return flow.restCustomSteps().splitterSteps().loadConfig(request);
    }

    private ValidatableResponseWrapper split(FlowWithRest flow, SplitRequestDto request) {
        return flow.restCustomSteps().splitterSteps().split(request);
    }

    private ValidatableResponseWrapper calculatePreliminary(FlowWithRest flow, SplitterPrecalcRequestDto request) {
        return flow.restCustomSteps().splitterSteps().calculatePreliminary(request);
    }

    private String uniqueId(String prefix) {
        return prefix + "-" + SplitterVersionProvider.next();
    }
}
