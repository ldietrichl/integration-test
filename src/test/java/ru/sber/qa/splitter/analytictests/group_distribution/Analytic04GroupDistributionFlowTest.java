package ru.sber.qa.splitter.analytictests.group_distribution;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
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
public class Analytic04GroupDistributionFlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: У эксперимента одна группа занимает весь диапазон; проверяем корректность выбора группы.")
    @DisplayName("AN-GROUP-01. Группа с диапазоном 0..10000 гарантированно выбирается")
    void fullRangeGroupShouldAlwaysBeSelected() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7401, "AN-GROUP-01", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto split = splitRequest("AN-GROUP-01", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с одной full-range группой", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что MAIN попал в группу A", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertMainExp(response, MATCHING_OBJECT_ID, 7401L);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: У эксперимента одна группа занимает не весь диапазон; при попадании вне интервала MAIN не должен формироваться.")
    @DisplayName("AN-GROUP-02. Объект вне всех share-диапазонов не получает MAIN")
    void objectOutsideAllGroupRangesShouldNotHaveMainAssignment() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7402, "AN-GROUP-02", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(group("A", 0, 1, 1, "0"))));
        SplitRequestDto split = splitRequest("AN-GROUP-02", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с очень узким диапазоном группы", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что объект связан, но MAIN assignment отсутствует при miss по диапазону", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertObjectHasAllButNoMainAssignment(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: У эксперимента несколько групп занимают весь диапазон; проверяем корректность выбора группы по spreadValue.")
    @DisplayName("AN-GROUP-03. Несколько групп покрывают разные диапазоны и выбирается группа по spreadValue")
    void multipleGroupsShouldSelectGroupBySpreadValue() {
        long version = SplitterVersionProvider.next();
        String salt = "AN-GROUP-03-SALT";
        String splitForA = splittingIdForRange("AN-GROUP-03-A", salt, 0, 3000);
        String splitForB = splittingIdForRange("AN-GROUP-03-B", salt, 3000, 7000);
        String splitForC = splittingIdForRange("AN-GROUP-03-C", salt, 7000, 10000);
        LoadConfigRequestDto config = config(version, experiment(7403, salt,
                List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                List.of(
                        group("A", 0, 3000, 1, "0"),
                        group("B", 3000, 7000, 1, "1"),
                        group("C", 7000, 10000, 1, "5"))));

        getFlowWithRest()
                .step("Загружаем config с группами A/B/C", flow -> loadConfigStep(flow, config))
                .step("Проверяем попадание в A", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitForA, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                })
                .step("Проверяем попадание в B", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitForB, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainGroup(response, MATCHING_OBJECT_ID, "B");
                    assertMainActionType(response, MATCHING_OBJECT_ID, "1");
                })
                .step("Проверяем попадание в C", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitForC, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainGroup(response, MATCHING_OBJECT_ID, "C");
                    assertMainActionType(response, MATCHING_OBJECT_ID, "5");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Все объекты привязались к экспериментам; проверяем результат по каждому объекту batch-запроса.")
    @DisplayName("AN-GROUP-04. Batch: все объекты, подходящие под условия, получают MAIN и ALL")
    void batchWithOnlyMatchedObjectsShouldReturnResultsForEveryObject() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7404, "AN-GROUP-04", List.of(objectParamInCondition(1, "id", "INTEGER", "1", "2", "3")), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto split = splitRequest("AN-GROUP-04",
                object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")),
                object(SECOND_OBJECT_ID, param("id", "2", "INTEGER")),
                object(THIRD_OBJECT_ID, param("id", "3", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config, привязывающий все batch-объекты", flow -> loadConfigStep(flow, config))
                .step("Проверяем MAIN/ALL для каждого объекта", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertObjectHasMainAndAll(response, SECOND_OBJECT_ID);
                    assertObjectHasMainAndAll(response, THIRD_OBJECT_ID);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7404L);
                    assertMainExp(response, SECOND_OBJECT_ID, 7404L);
                    assertMainExp(response, THIRD_OBJECT_ID, 7404L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем попадание распределения на начало и конец интервала группы, включая внутренние границы.")
    @DisplayName("AN-GROUP-05. Граничные значения shareFrom/shareTo и внутренние границы")
    void groupBoundaryValuesShouldBeCoveredByDedicatedSpreadMatrix() {
        long version = SplitterVersionProvider.next();
        String salt = "AN-GROUP-05-SALT";
        String splitAtStart = splittingIdForExactSpread("AN-GROUP-05-SP0", salt, 0);
        String splitBeforeInnerBoundary = splittingIdForExactSpread("AN-GROUP-05-SP2999", salt, 2999);
        String splitAtInnerBoundary = splittingIdForExactSpread("AN-GROUP-05-SP3000", salt, 3000);
        String splitBeforeSecondBoundary = splittingIdForExactSpread("AN-GROUP-05-SP6999", salt, 6999);
        String splitAtSecondBoundary = splittingIdForExactSpread("AN-GROUP-05-SP7000", salt, 7000);
        String splitAtEnd = splittingIdForExactSpread("AN-GROUP-05-SP9999", salt, 9999);
        LoadConfigRequestDto config = config(version, experiment(7405, salt,
                List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                List.of(
                        group("A", 0, 3000, 1, "0"),
                        group("B", 3000, 7000, 1, "1"),
                        group("C", 7000, 10000, 1, "5"))));

        getFlowWithRest()
                .step("Загружаем config с граничными диапазонами A[0;3000), B[3000;7000), C[7000;10000)", flow -> loadConfigStep(flow, config))
                .step("spreadValue=0 попадает в начало интервала A", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitAtStart, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 0);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                })
                .step("spreadValue=2999 остается в интервале A", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitBeforeInnerBoundary, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 2999);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                })
                .step("spreadValue=3000 попадает в начало интервала B", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitAtInnerBoundary, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 3000);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "B");
                })
                .step("spreadValue=6999 остается в интервале B", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitBeforeSecondBoundary, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 6999);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "B");
                })
                .step("spreadValue=7000 попадает в начало интервала C", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitAtSecondBoundary, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 7000);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "C");
                })
                .step("spreadValue=9999 остается в последнем допустимом значении C", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitAtEnd, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 9999);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "C");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: У группы два интервала, идущих подряд; проверяем попадание на внутреннюю границу внутри одной группы.")
    @DisplayName("AN-GROUP-06. Одна группа с двумя соседними интервалами покрывает внутреннюю границу")
    void singleGroupWithTwoAdjacentRangesShouldCoverInternalBoundary() {
        long version = SplitterVersionProvider.next();
        String salt = "AN-GROUP-06-SALT";
        String splitBeforeBoundary = splittingIdForExactSpread("AN-GROUP-06-SP2999", salt, 2999);
        String splitAtBoundary = splittingIdForExactSpread("AN-GROUP-06-SP3000", salt, 3000);
        LoadConfigRequestDto config = config(version, experiment(7406, salt,
                List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                List.of(
                        group("A", List.of(share(0, 3000), share(3000, 7000)), 1, "0"),
                        group("B", 7000, 10000, 1, "1"))));

        getFlowWithRest()
                .step("Загружаем config, где группа A состоит из двух соседних диапазонов", flow -> loadConfigStep(flow, config))
                .step("spreadValue=2999 попадает в первый диапазон группы A", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitBeforeBoundary, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 2999);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                })
                .step("spreadValue=3000 попадает во второй соседний диапазон той же группы A", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitAtBoundary, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")))));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 3000);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                })
                .run();
    }

}
