package ru.sber.qa.splitter.analytictests.precalc;


import ru.sber.qa.allure.ManualTest;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;

import static request.splitter.SplitterPrecalcTestDataFactory.GUARANTEED_POSITIVE_EXP_ID;
import static request.splitter.SplitterPrecalcTestDataFactory.changedGuaranteedPositivePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.changedGuaranteedPositiveSplitRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedNegativePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedPositiveConfig;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedPositivePrecalcRequest;
import static request.splitter.SplitterPrecalcTestDataFactory.minimalGuaranteedPositiveSplitRequest;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoaded;
import static util.SplitterPrecalcAssertions.shouldContainExpId;
import static util.SplitterPrecalcAssertions.shouldHaveNonEmptySplitEnvelope;
import static util.SplitterPrecalcAssertions.shouldHaveSingleObjectWithEmptyResults;
import static util.SplitterPrecalcAssertions.shouldHaveSingleObjectWithNonEmptyResults;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
/**
 * REST-only analytic splitter coverage class.
 *
 * Required ConfigMap contract: src/test/resources/splitter/configmap/mapper-current.yml.
 * The test does not apply ConfigMap automatically; the target environment must be configured with compatible rules.
 */
public class Analytic06PrecalcFlowTest extends AbstractAnalyticSplitterFlowTest {

    private static final int SO_CONFIG_VERSION = 1;
    private static final String PRECALC_OBJECT_ID = request.splitter.SplitterPrecalcTestDataFactory.MATCHING_OBJECT_ID;

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Добавляем в предрасчет запрашиваемый объект и делаем сплиттование; результат должен быть корректный и непустой.")
    @DisplayName("AN-PREC-01. Предрасчитанный object по uniqueConfigurationId возвращает непустой split result")
    void precalculatedObjectShouldReturnNonEmptySplitResult() {
        long version = SplitterVersionProvider.next();
        String uniqueConfigurationId = uniqueId("an-prec-01");
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        SplitterPrecalcRequestDto precalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto split = minimalGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем minimal guaranteed-positive config", flow -> shouldBeConfigLoaded(shouldBe200(load(flow, config))))
                .step("Выполняем predcalc для объекта", flow -> shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, precalc)), SO_CONFIG_VERSION))
                .step("Проверяем, что split по uniqueConfigurationId вернул связанный эксперимент", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, split));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, PRECALC_OBJECT_ID);
                    shouldContainExpId(response, PRECALC_OBJECT_ID, GUARANTEED_POSITIVE_EXP_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Делаем сплиттование без предрасчета и с предрасчетом; результат должен быть одинаково корректным.")
    @DisplayName("AN-PREC-02. Runtime split до и после predcalc возвращает одинаково непустой результат")
    void runtimeSplitBeforeAndAfterPrecalcShouldStayPositive() {
        long version = SplitterVersionProvider.next();
        String uniqueConfigurationId = uniqueId("an-prec-02");
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        SplitterPrecalcRequestDto precalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto split = minimalGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBeConfigLoaded(shouldBe200(load(flow, config))))
                .step("До predcalc split уже должен быть корректным", flow -> shouldHaveSingleObjectWithNonEmptyResults(shouldBe200(split(flow, split)), PRECALC_OBJECT_ID))
                .step("Выполняем predcalc", flow -> shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, precalc)), SO_CONFIG_VERSION))
                .step("После predcalc split остается корректным", flow -> shouldHaveSingleObjectWithNonEmptyResults(shouldBe200(split(flow, split)), PRECALC_OBJECT_ID))
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Делаем сплиттование при наличии предрасчета, но в нем нет запрашиваемого объекта.")
    @DisplayName("AN-PREC-03. Если объект не был добавлен в predcalc как positive, split возвращает пустой результат")
    void objectNotLinkedByPrecalcShouldReturnEmptyResult() {
        long version = SplitterVersionProvider.next();
        String uniqueConfigurationId = uniqueId("an-prec-03");
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        SplitterPrecalcRequestDto negativePrecalc = minimalGuaranteedNegativePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitRequestDto split = minimalGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBeConfigLoaded(shouldBe200(load(flow, config))))
                .step("Выполняем predcalc с объектом, который не должен связаться", flow -> shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, negativePrecalc)), SO_CONFIG_VERSION))
                .step("Проверяем, что split по uniqueConfigurationId не вернул objectResults", flow -> shouldHaveSingleObjectWithEmptyResults(shouldBe200(split(flow, split)), PRECALC_OBJECT_ID))
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Изменяем конфигурацию объекта в запросе после предрасчета; результат не должен измениться для того же uniqueConfigurationId.")
    @DisplayName("AN-PREC-04. Изменение параметров объекта при том же uniqueConfigurationId не меняет результат")
    void changedObjectParamsForSameUniqueIdShouldNotChangePrecalculatedResult() {
        long version = SplitterVersionProvider.next();
        String uniqueConfigurationId = uniqueId("an-prec-04");
        LoadConfigRequestDto config = minimalGuaranteedPositiveConfig(version);
        SplitterPrecalcRequestDto precalc = minimalGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION);
        SplitterPrecalcRequestDto changedPrecalc = changedGuaranteedPositivePrecalcRequest(uniqueConfigurationId, SO_CONFIG_VERSION + 1);
        SplitRequestDto changedSplit = changedGuaranteedPositiveSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBeConfigLoaded(shouldBe200(load(flow, config))))
                .step("Выполняем первый predcalc", flow -> shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, precalc)), SO_CONFIG_VERSION))
                .step("Повторяем predcalc с измененными параметрами", flow -> shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, changedPrecalc)), SO_CONFIG_VERSION + 1))
                .step("Проверяем, что split по тому же uniqueConfigurationId остается непустым", flow -> shouldHaveSingleObjectWithNonEmptyResults(shouldBe200(split(flow, changedSplit)), PRECALC_OBJECT_ID))
                .run();
    }

    @Disabled("Performance-сравнение до/после predcalc требует нагрузочного профиля и метрик latency, а не функционального REST flow")
    @ManualTest
    @Test
    @AnalyticTag("Аналитика: Делаем сплиттование с предрасчетом, засекаем временные параметры; должно быть быстрее.")
    @DisplayName("AN-PREC-05. Split с predcalc быстрее split без predcalc")
    void precalcShouldImprovePerformanceUnderLoad() {
    }

    private ValidatableResponseWrapper calculatePreliminary(FlowWithRest flow, SplitterPrecalcRequestDto request) {
        return flow.restCustomSteps().splitterSteps().calculatePreliminary(request);
    }

    private String uniqueId(String prefix) {
        return prefix + "-" + SplitterVersionProvider.next();
    }
}
