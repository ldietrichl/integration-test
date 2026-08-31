package ru.sber.qa.splitter.analytictests.layers;


import ru.sber.qa.splitter.support.AnyConfigLoadMode;
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
@AnyConfigLoadMode
public class Analytic10LayerPriorityFlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: На одном объекте эксперименты без слоя и со слоями; выбор происходит по приоритету слоя только из экспериментов со слоями.")
    @DisplayName("AN-LAYER-01. Среди экспериментов со слоями MAIN выбирается по layerPriority")
    void layeredExperimentsShouldChooseMainByLayerPriority() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                layeredExperiment(8001, "AN-LAYER-01-A", 1, 10, List.of(objectParamEqualsCondition(1, "ikp", "true", "BOOLEAN")), List.of(fullRangeGroup("A", 1, "1"))),
                layeredExperiment(8002, "AN-LAYER-01-B", 2, 5, List.of(objectParamEqualsCondition(1, "ikp", "true", "BOOLEAN")), List.of(fullRangeGroup("A", 1, "1"))));
        SplitRequestDto split = splitRequest("AN-LAYER-01", object(MATCHING_OBJECT_ID, param("ikp", "true", "BOOLEAN")));

        getFlowWithRest()
                .step("Загружаем config с двумя слоями", flow -> loadConfigStep(flow, config))
                .step("Проверяем выбор MAIN по приоритету слоя", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertMainExp(response, MATCHING_OBJECT_ID, 8001L);
                    assertMainLayer(response, MATCHING_OBJECT_ID, 1, 10);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Несколько экспериментов с одинаковыми слоями на объекте; должен выбираться правильный приоритет и среди них по id.")
    @DisplayName("AN-LAYER-02. При одинаковом layerPriority tie-break выполняется по expId")
    void equalLayerPriorityShouldChooseMinimalExpId() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                layeredExperiment(8012, "AN-LAYER-02-A", 1, 5, List.of(objectParamEqualsCondition(1, "ikp", "true", "BOOLEAN")), List.of(fullRangeGroup("A", 1, "1"))),
                layeredExperiment(8011, "AN-LAYER-02-B", 1, 5, List.of(objectParamEqualsCondition(1, "ikp", "true", "BOOLEAN")), List.of(fullRangeGroup("A", 1, "1"))));
        SplitRequestDto split = splitRequest("AN-LAYER-02", object(MATCHING_OBJECT_ID, param("ikp", "true", "BOOLEAN")));

        getFlowWithRest()
                .step("Загружаем config с одинаковым layerPriority", flow -> loadConfigStep(flow, config))
                .step("Проверяем tie-break по минимальному expId", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertMainExp(response, MATCHING_OBJECT_ID, 8011L);
                    assertMainLayer(response, MATCHING_OBJECT_ID, 1, 5);
                })
                .run();
    }


    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: На одном объекте эксперименты без слоя. Выбор происходит по id.")
    @DisplayName("AN-LAYER-03. Без слоя MAIN выбирается по expId при одинаковом actionType")
    void experimentsWithoutLayerShouldChooseMinimalExpIdWhenPriorityIsEqual() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(8022, "AN-LAYER-03-A", List.of(objectParamEqualsCondition(1, "ikp", "true", "BOOLEAN")), List.of(fullRangeGroup("A", 1, "1"))),
                experiment(8021, "AN-LAYER-03-B", List.of(objectParamEqualsCondition(1, "ikp", "true", "BOOLEAN")), List.of(fullRangeGroup("A", 1, "1"))));
        SplitRequestDto split = splitRequest("AN-LAYER-03", object(MATCHING_OBJECT_ID, param("ikp", "true", "BOOLEAN")));

        getFlowWithRest()
                .step("Загружаем два эксперимента без layerId/layerPriority", flow -> loadConfigStep(flow, config))
                .step("Проверяем tie-break по минимальному expId", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertMainExp(response, MATCHING_OBJECT_ID, 8021L);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "1");
                })
                .run();
    }


    @Disabled("Нужна управляемая ConfigMap: переключение min/max выбора layerPriority/id не доступно из текущего REST flow")
    @ManualTest
    @Test
    @AnalyticTag("Аналитика: Меняем в конфигурации параметр выбора по приоритету слоя min/max и параметр выбора по id min/max.")
    @DisplayName("AN-LAYER-04. Переключение min/max правил выбора layerPriority и expId")
    void layerPriorityMinMaxConfigShouldBeCoveredWithConfigMapMatrix() {
    }
}
