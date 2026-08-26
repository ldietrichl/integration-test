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
import util.splittercheck.SplitterCheckMode;
import util.splittercheck.strict.SplitterPrecalcStrictChecks;
import util.splittercheck.strict.SplitterStrictChecks;
import ru.sber.qa.allure.CriticalRegression;

import static request.splitter.SplitterPrecalcTestDataFactory.GUARANTEED_POSITIVE_EXP_ID;
import static request.splitter.SplitterPrecalcTestDataFactory.MATCHING_OBJECT_ID;
import static request.splitter.SplitterPrecalcTestDataFactory.MULTI_CONDITION_EXP_ID;
import static request.splitter.SplitterPrecalcTestDataFactory.MULTI_CONDITION_RESULT_CONDITION_ID;
import static request.splitter.SplitterPrecalcTestDataFactory.RELOAD_BASELINE_EXP_ID;
import static request.splitter.SplitterPrecalcTestDataFactory.RELOAD_CHANGED_EXP_ID;
import static request.splitter.SplitterPrecalcTestDataFactory.changedGuaranteedPositivePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.changedGuaranteedPositiveSplitRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.emptyPrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.matchingMapperConfigMessage;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedNegativePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedNegativeSplitRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedPositiveConfig;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedPositivePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.multiConditionPrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.multiConditionSingleExperimentConfig;
import static request.splitter.SplitterPrecalcTestDataFactory.multiConditionSplitRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.precalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.reloadSensitiveConfigV1;
import static request.splitter.SplitterPrecalcTestDataFactory.reloadSensitiveConfigV2;
import static request.splitter.SplitterPrecalcTestDataFactory.reloadSensitivePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.reloadSensitiveSplitRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.sameShapeMatchingPrecalcObject;
import static request.splitter.SplitterPrecalcTestDataFactory.sameShapeMatchingSplitObject;
import static request.splitter.SplitterPrecalcTestDataFactory.sameShapeNonMatchingPrecalcObject;
import static request.splitter.SplitterPrecalcTestDataFactory.sameShapeNonMatchingSplitObject;
import static request.splitter.SplitterPrecalcTestDataFactory.splitRequest;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoaded;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoadedOrLoadedWithPrecalc;
import static util.SplitterPrecalcAssertions.shouldContainConditionId;
import static util.SplitterPrecalcAssertions.shouldContainExpId;
import static util.SplitterPrecalcAssertions.shouldContainObjectIds;
import static util.SplitterPrecalcAssertions.shouldHaveJsonBody;
import static util.SplitterPrecalcAssertions.shouldHaveNonEmptySplitEnvelope;
import static util.SplitterPrecalcAssertions.shouldHaveResponseId;
import static util.SplitterPrecalcAssertions.shouldHaveObjectWithEmptyResults;
import static util.SplitterPrecalcAssertions.shouldHaveObjectWithNonEmptyResults;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;
import static util.SplitterPrecalcAssertions.shouldHaveSplittingResultsSize;
import static util.SplitterPrecalcAssertions.shouldNotContainExpId;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
public class SplitterPrecalcRiskFlowTest extends AbstractNewSplitterFlowTest {

    private static final int SO_CONFIG_VERSION = 1;
    private static final SplitterCheckMode CHECK_MODE = SplitterCheckMode.fromSystemProperty();
    private static final String BATCH_MATCHING_OBJECT_ID = "33333333-3333-3333-3333-333333333333";
    private static final String BATCH_NON_MATCHING_OBJECT_ID = "44444444-4444-4444-4444-444444444444";
    private static final String MULTI_CONDITION_OBJECT_ID = "55555555-5555-5555-5555-555555555555";
    private static final String RELOAD_OBJECT_ID = "66666666-6666-6666-6666-666666666666";

    @CriticalRegression
    @Test
    @DisplayName("PC-R-01. batch из однотипных объектов не должен ломать predcalc")
    void sameShapeBatchShouldNotFailDuringPrecalc() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);
        String matchingUniqueConfigurationId = uniqueId("pc-r-01-match");
        String nonMatchingUniqueConfigurationId = uniqueId("pc-r-01-non-match");

        SplitterPrecalcRequestDto precalcRequest = precalcRequest(
                SO_CONFIG_VERSION,
                sameShapeMatchingPrecalcObject(matchingUniqueConfigurationId),
                sameShapeNonMatchingPrecalcObject(nonMatchingUniqueConfigurationId)
        );

        SplitRequestDto splitRequest = splitRequest(
                sameShapeMatchingSplitObject(matchingUniqueConfigurationId, BATCH_MATCHING_OBJECT_ID),
                sameShapeNonMatchingSplitObject(nonMatchingUniqueConfigurationId, BATCH_NON_MATCHING_OBJECT_ID)
        );

        getFlowWithRest()
                .step("Загружаем config для batch-проверки", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(response);
                })
                .step("Выполняем predcalc для batch из однотипных объектов", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    shouldHaveJsonBody(response);
                    shouldHaveResponseId(response);
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                    SplitterPrecalcStrictChecks.verifyDocumentedPrecalcSuccessEnvelope(response, CHECK_MODE);
                })
                .step("Split не должен ломаться и должен вернуть оба объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSplittingResultsSize(response, 2);
                    shouldContainObjectIds(response, BATCH_MATCHING_OBJECT_ID, BATCH_NON_MATCHING_OBJECT_ID);
                    shouldHaveObjectWithNonEmptyResults(response, BATCH_MATCHING_OBJECT_ID);
                    shouldHaveObjectWithEmptyResults(response, BATCH_NON_MATCHING_OBJECT_ID);
                    SplitterStrictChecks.verifyRuleCodes(response, BATCH_MATCHING_OBJECT_ID, java.util.Set.of("MAIN", "ALL"), CHECK_MODE);
                    SplitterStrictChecks.verifyMainIsSubsetOfAll(response, BATCH_MATCHING_OBJECT_ID, CHECK_MODE);
                    SplitterStrictChecks.verifyNoDuplicateExpIds(response, BATCH_MATCHING_OBJECT_ID, CHECK_MODE);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-R-02. объект с тем же uniqueConfigurationId сохраняет старую precalc-связь")
    void sameUniqueConfigurationIdShouldKeepOldPrecalcLink() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-r-02");
        SplitterPrecalcRequestDto baselinePrecalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitterPrecalcRequestDto changedPrecalc = changedGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto changedSplitRequest = changedGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем guaranteed-positive config и строим baseline таблицу", flow -> {
                    ValidatableResponseWrapper configResponse = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(configResponse);
                    SplitterPrecalcStrictChecks.verifyDocumentedConfigLoadEnvelope(configResponse, CHECK_MODE);
                    ValidatableResponseWrapper predcalcResponse = shouldBe200(calculatePreliminary(flow, baselinePrecalc));
                    shouldHaveJsonBody(predcalcResponse);
                    shouldHaveResponseId(predcalcResponse);
                    shouldHaveSoConfigVersion(predcalcResponse, SO_CONFIG_VERSION);
                    SplitterPrecalcStrictChecks.verifyDocumentedPrecalcSuccessEnvelope(predcalcResponse, CHECK_MODE);
                })
                .step("Повторно загружаем измененный объект с тем же uniqueConfigurationId", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, changedPrecalc));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("Split продолжает использовать старую precalc-связь", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, changedSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                    SplitterStrictChecks.verifyRuleCodes(response, MATCHING_OBJECT_ID, java.util.Set.of("MAIN", "ALL"), CHECK_MODE);
                    SplitterStrictChecks.verifyMainIsSubsetOfAll(response, MATCHING_OBJECT_ID, CHECK_MODE);
                    shouldContainExpId(response, MATCHING_OBJECT_ID, GUARANTEED_POSITIVE_EXP_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-R-03. пустой predcalc действительно очищает старую таблицу")
    void emptyPrecalcShouldRemoveOldLinkUsage() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-r-03");
        SplitterPrecalcRequestDto baselinePrecalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitterPrecalcRequestDto emptyPrecalc = emptyPrecalcRequest(SO_CONFIG_VERSION);
        SplitRequestDto changedSplitRequest = changedGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем config и строим baseline таблицу", flow -> {
                    ValidatableResponseWrapper configResponse = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(configResponse);
                    SplitterPrecalcStrictChecks.verifyDocumentedConfigLoadEnvelope(configResponse, CHECK_MODE);
                    ValidatableResponseWrapper predcalcResponse = shouldBe200(calculatePreliminary(flow, baselinePrecalc));
                    shouldHaveJsonBody(predcalcResponse);
                    shouldHaveResponseId(predcalcResponse);
                    shouldHaveSoConfigVersion(predcalcResponse, SO_CONFIG_VERSION);
                    SplitterPrecalcStrictChecks.verifyDocumentedPrecalcSuccessEnvelope(predcalcResponse, CHECK_MODE);
                })
                .step("До очистки таблицы split использует старую precalc-связь", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, changedSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .step("Пустой predcalc очищает таблицу", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, emptyPrecalc));
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("После очистки измененный объект больше не использует старую precalc-связь", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, changedSplitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveObjectWithEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-R-04. один experiment с несколькими condition не должен терять результат после predcalc")
    void multiConditionSingleExperimentShouldKeepRuntimeEquivalentResultAfterPrecalc() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = multiConditionSingleExperimentConfig(version);
        String uniqueConfigurationId = uniqueId("pc-r-04");
        SplitterPrecalcRequestDto precalcRequest = multiConditionPrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto splitRequest = multiConditionSplitRequest(uniqueConfigurationId, MULTI_CONDITION_OBJECT_ID);

        getFlowWithRest()
                .step("Загружаем config с несколькими condition у одного experiment", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(response);
                })
                .step("До predcalc runtime split уже должен возвращать ожидаемую связь", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveObjectWithNonEmptyResults(response, MULTI_CONDITION_OBJECT_ID);
                    SplitterStrictChecks.verifyRuleCodes(response, MULTI_CONDITION_OBJECT_ID, java.util.Set.of("MAIN", "ALL"), CHECK_MODE);
                    SplitterStrictChecks.verifyMainIsSubsetOfAll(response, MULTI_CONDITION_OBJECT_ID, CHECK_MODE);
                    shouldContainExpId(response, MULTI_CONDITION_OBJECT_ID, MULTI_CONDITION_EXP_ID);
                    shouldContainConditionId(response, MULTI_CONDITION_OBJECT_ID, MULTI_CONDITION_RESULT_CONDITION_ID);
                })
                .step("Строим precalc-таблицу для того же объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    shouldHaveJsonBody(response);
                    shouldHaveResponseId(response);
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                    SplitterPrecalcStrictChecks.verifyDocumentedPrecalcSuccessEnvelope(response, CHECK_MODE);
                })
                .step("После predcalc результат не должен потеряться", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveObjectWithNonEmptyResults(response, MULTI_CONDITION_OBJECT_ID);
                    SplitterStrictChecks.verifyRuleCodes(response, MULTI_CONDITION_OBJECT_ID, java.util.Set.of("MAIN", "ALL"), CHECK_MODE);
                    SplitterStrictChecks.verifyMainIsSubsetOfAll(response, MULTI_CONDITION_OBJECT_ID, CHECK_MODE);
                    shouldContainExpId(response, MULTI_CONDITION_OBJECT_ID, MULTI_CONDITION_EXP_ID);
                    shouldContainConditionId(response, MULTI_CONDITION_OBJECT_ID, MULTI_CONDITION_RESULT_CONDITION_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-R-05. reload config при существующей precalc-таблице должен перевязать объект на новый experiment")
    void reloadConfigWithExistingPrecalcShouldRebindToNewExperiment() {
        long firstVersion = SplitterVersionProvider.next();
        long secondVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto configV1 = reloadSensitiveConfigV1(firstVersion);
        LoadConfigRequestDto configV2 = reloadSensitiveConfigV2(secondVersion);
        String uniqueConfigurationId = uniqueId("pc-r-05");
        SplitterPrecalcRequestDto precalcRequest = reloadSensitivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto splitRequest = reloadSensitiveSplitRequest(uniqueConfigurationId, RELOAD_OBJECT_ID);

        getFlowWithRest()
                .step("Загружаем baseline-config и строим precalc-таблицу", flow -> {
                    shouldBeConfigLoaded(shouldBe200(loadConfig(flow, configV1)));
                    shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, precalcRequest)), SO_CONFIG_VERSION);
                })
                .step("До reload объект ссылается на baseline experiment", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveObjectWithNonEmptyResults(response, RELOAD_OBJECT_ID);
                    SplitterStrictChecks.verifyRuleCodes(response, RELOAD_OBJECT_ID, java.util.Set.of("MAIN", "ALL"), CHECK_MODE);
                    SplitterStrictChecks.verifyMainIsSubsetOfAll(response, RELOAD_OBJECT_ID, CHECK_MODE);
                    shouldContainExpId(response, RELOAD_OBJECT_ID, RELOAD_BASELINE_EXP_ID);
                })
                .step("Перезагружаем config при уже существующей precalc-таблице", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, configV2));
                    shouldBeConfigLoadedOrLoadedWithPrecalc(response);
                })
                .step("После reload объект должен ссылаться на новый experiment", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveObjectWithNonEmptyResults(response, RELOAD_OBJECT_ID);
                    SplitterStrictChecks.verifyRuleCodes(response, RELOAD_OBJECT_ID, java.util.Set.of("MAIN", "ALL"), CHECK_MODE);
                    SplitterStrictChecks.verifyMainIsSubsetOfAll(response, RELOAD_OBJECT_ID, CHECK_MODE);
                    shouldContainExpId(response, RELOAD_OBJECT_ID, RELOAD_CHANGED_EXP_ID);
                    shouldNotContainExpId(response, RELOAD_OBJECT_ID, RELOAD_BASELINE_EXP_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-R-06. успешный predcalc сам по себе не гарантирует business-link")
    void successfulPrecalcShouldNotBeTreatedAsGuaranteedBusinessLink() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        String uniqueConfigurationId = uniqueId("pc-r-06");
        SplitterPrecalcRequestDto precalcRequest = minimalGuaranteedNegativePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto splitRequest = minimalGuaranteedNegativeSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем guaranteed-positive config", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(response);
                })
                .step("Predcalc отрабатывает технически успешно", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    shouldHaveJsonBody(response);
                    shouldHaveResponseId(response);
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                    SplitterPrecalcStrictChecks.verifyDocumentedPrecalcSuccessEnvelope(response, CHECK_MODE);
                })
                .step("Но business-link для guaranteed-negative объекта так и не появляется", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveObjectWithEmptyResults(response, MATCHING_OBJECT_ID);
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
