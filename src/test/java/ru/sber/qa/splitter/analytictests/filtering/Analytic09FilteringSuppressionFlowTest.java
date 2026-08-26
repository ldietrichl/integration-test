package ru.sber.qa.splitter.analytictests.filtering;


import ru.sber.qa.allure.ManualTest;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
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
import util.support.SplitterVersionProvider;

import java.util.List;

import static util.SplitterPrecalcAssertions.shouldBe200;

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
public class Analytic09FilteringSuppressionFlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем фильтрацию по actionType; actionType не из списка фильтруемых должен давать filtered=false.")
    @DisplayName("AN-FILT-01. Нефильтруемый actionType возвращает объект с filtered=false")
    void nonFilteredActionTypeShouldReturnObjectWithFilteredFalse() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7901, "AN-FILT-01", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto split = splitRequest("AN-FILT-01", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с actionType=0", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что объект присутствует и filtered=false", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertFiltered(response, MATCHING_OBJECT_ID, "false");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Делаем эксперименты с actionType из списка фильтруемых. Объект должен получать флаг filtered=true.")
    @DisplayName("AN-FILT-02. Фильтруемый actionType из текущей ConfigMap возвращает объект с filtered=true")
    void filteredActionTypeShouldSuppressObjectOnCurrentConfigMap() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7902, "AN-FILT-02", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "2"))));
        SplitRequestDto split = splitRequest("AN-FILT-02", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с actionType=2 из filter-rule.values текущей ConfigMap", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что объект присутствует и размечен filtered=true", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, version);
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertFiltered(response, MATCHING_OBJECT_ID, "true");
                    assertMainActionType(response, MATCHING_OBJECT_ID, "2");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Отдавать отфильтрованные = true. Объекты с filtered=true должны попадать в ответ.")
    @DisplayName("AN-FILT-03. При текущей ConfigMap объект с filtered=true возвращается в ответе")
    void filteredObjectsShouldBeReturnedWhenConfigured() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7903, "AN-FILT-03", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "4"))));
        SplitRequestDto split = splitRequest("AN-FILT-03", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с actionType=4 из filter-rule.values текущей ConfigMap", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что filtered=true объект остается в response", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertFiltered(response, MATCHING_OBJECT_ID, "true");
                    assertMainActionType(response, MATCHING_OBJECT_ID, "4");
                })
                .run();
    }

    @Disabled("Нужна ConfigMap с alternative rollback: комбинация filtered + alternative rollback не меняется из текущего REST flow")
    @ManualTest
    @Test
    @AnalyticTag("Аналитика: Комбинируем фильтрацию на объекте с альтернативой и откатом альтернативы.")
    @DisplayName("AN-FILT-04. Фильтрация в комбинации с alternative rollback")
    void filteringShouldRespectAlternativeRollback() {
    }
}
