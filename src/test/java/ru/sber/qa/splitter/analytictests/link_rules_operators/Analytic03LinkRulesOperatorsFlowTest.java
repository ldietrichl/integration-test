package ru.sber.qa.splitter.analytictests.link_rules_operators;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
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
public class Analytic03LinkRulesOperatorsFlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем работу правил привязки без ИЛИ; несколько выражений внутри блока должны выполняться как AND.")
    @DisplayName("AN-RULES-01. AND внутри блока: объект привязывается только при выполнении всех выражений")
    void andInsideRuleBlockShouldRequireAllExpressions() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto condition = condition(1, List.of(List.of(
                rule("INTEGER", "channelId", "SPLITTING_OBJECTS", "equal", "20"),
                rule("STRING", "productId", "SPLITTING_OBJECTS", "equal", "P1"))));
        LoadConfigRequestDto config = config(version, experiment(7301, "AN-RULES-01", List.of(condition), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto positive = splitRequest("AN-RULES-01-P", object(MATCHING_OBJECT_ID, param("channelId", "20", "INTEGER"), param("productId", "P1", "STRING")));
        SplitRequestDto negative = splitRequest("AN-RULES-01-N", object(NEGATIVE_OBJECT_ID, param("channelId", "20", "INTEGER"), param("productId", "P2", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с AND-условием", flow -> loadConfigStep(flow, config))
                .step("Позитивный объект выполняет оба выражения", flow -> {
                    var response = shouldBe200(split(flow, positive));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7301L);
                })
                .step("Негативный объект не выполняет одно выражение и не получает MAIN", flow -> {
                    var response = shouldBe200(split(flow, negative));
                    assertObjectHasAllButNoMainAssignment(response, NEGATIVE_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем условие с ИЛИ; объект должен привязаться при выполнении любого блока OR.")
    @DisplayName("AN-RULES-02. OR между блоками: объект привязывается при выполнении любого блока")
    void orBetweenRuleBlocksShouldMatchAnyBlock() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto condition = condition(1, List.of(
                List.of(rule("INTEGER", "channelId", "SPLITTING_OBJECTS", "equal", "20")),
                List.of(rule("STRING", "productId", "SPLITTING_OBJECTS", "equal", "P2")),
                List.of(rule("INTEGER", "templateId", "SPLITTING_OBJECTS", "equal", "777"))));
        LoadConfigRequestDto config = config(version, experiment(7302, "AN-RULES-02", List.of(condition), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest("AN-RULES-02", object(MATCHING_OBJECT_ID, param("productId", "P2", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с тремя OR-блоками", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что срабатывание второго OR-блока достаточно для привязки", flow -> {
                    var response = shouldBe200(split(flow, request));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7302L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Условие с ИЛИ и отсутствующий параметр в одном блоке выражений; объект должен привязаться по выражениям, где нет отсутствующего параметра.")
    @DisplayName("AN-RULES-03. Отсутствующий параметр в одном OR-блоке не мешает привязке по другому блоку")
    void missingParamInOneOrBlockShouldNotBlockOtherMatchingBlock() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto condition = condition(1, List.of(
                List.of(rule("INTEGER", "absentParam", "SPLITTING_OBJECTS", "equal", "1")),
                List.of(rule("INTEGER", "channelId", "SPLITTING_OBJECTS", "equal", "20"))));
        LoadConfigRequestDto config = config(version, experiment(7303, "AN-RULES-03", List.of(condition), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest("AN-RULES-03", object(MATCHING_OBJECT_ID, param("channelId", "20", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с отсутствующим параметром в одном OR-блоке", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что объект привязался по второму блоку", flow -> {
                    var response = shouldBe200(split(flow, request));
                    assertMainExp(response, MATCHING_OBJECT_ID, 7303L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем работу реализуемой через REST матрицы операторов: equal, not_equal, more, less, more_equal, less_equal, in, not_in, is_not_null для INTEGER и STRING. Null-семантика проверяется отдельно.")
    @DisplayName("AN-RULES-04. Матрица базовых операторов rules для INTEGER/STRING")
    void coreOperatorsAndDataTypesShouldBeCoveredByRestMatrix() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7311, "AN-RULES-04-EQ", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "equal", "10"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7312, "AN-RULES-04-NE", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "not_equal", "5"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7313, "AN-RULES-04-MORE", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "more", "5"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7314, "AN-RULES-04-LESS", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "less", "15"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7315, "AN-RULES-04-MORE-EQ", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "more_equal", "10"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7316, "AN-RULES-04-LESS-EQ", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "less_equal", "10"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7317, "AN-RULES-04-IN", List.of(condition(1, List.of(List.of(rule("STRING", "status", "SPLITTING_OBJECTS", "in", "OK", "WARN"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7318, "AN-RULES-04-NOT-IN", List.of(condition(1, List.of(List.of(rule("STRING", "status", "SPLITTING_OBJECTS", "not_in", "FAIL", "BLOCKED"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7320, "AN-RULES-04-IS-NOT-NULL", List.of(condition(1, List.of(List.of(rule("STRING", "status", "SPLITTING_OBJECTS", "is_not_null"))))), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest("AN-RULES-04", object(MATCHING_OBJECT_ID,
                param("score", "10", "INTEGER"),
                param("status", "OK", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с REST-матрицей базовых операторов", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что объект привязался ко всем экспериментам базовой operator matrix", flow -> {
                    var response = shouldBe200(split(flow, request));
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID,
                            7311L, 7312L, 7313L, 7314L, 7315L, 7316L, 7317L, 7318L, 7320L);
                })
                .run();
    }


    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем is_null/is_not_null, когда параметр задан и содержит обычное значение.")
    @DisplayName("AN-RULES-07. is_null/is_not_null: параметр задан со значением")
    void isNullAndIsNotNullShouldHandlePresentValue() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7331, "AN-RULES-07-IS-NULL", List.of(condition(1, List.of(List.of(rule("STRING", "optionalField", "SPLITTING_OBJECTS", "is_null"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7332, "AN-RULES-07-IS-NOT-NULL", List.of(condition(1, List.of(List.of(rule("STRING", "optionalField", "SPLITTING_OBJECTS", "is_not_null"))))), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest("AN-RULES-07", object(MATCHING_OBJECT_ID,
                param("optionalField", "VALUE", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с is_null/is_not_null для параметра со значением", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что параметр со значением матчится только на is_not_null", flow -> {
                    var response = shouldBe200(split(flow, request));
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 7332L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем is_null/is_not_null, когда параметр задан явно и содержит JSON null.")
    @DisplayName("AN-RULES-08. is_null/is_not_null: параметр задан как null")
    void isNullAndIsNotNullShouldHandleExplicitNullValue() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7333, "AN-RULES-08-IS-NULL", List.of(condition(1, List.of(List.of(rule("STRING", "optionalField", "SPLITTING_OBJECTS", "is_null"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7334, "AN-RULES-08-IS-NOT-NULL", List.of(condition(1, List.of(List.of(rule("STRING", "optionalField", "SPLITTING_OBJECTS", "is_not_null"))))), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest("AN-RULES-08", object(MATCHING_OBJECT_ID,
                paramWithNullValue("optionalField", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с is_null/is_not_null для параметра, заданного как null", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что явно заданный null матчится только на is_null", flow -> {
                    var response = shouldBe200(split(flow, request));
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 7333L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем is_null/is_not_null, когда параметр не передан в objectParams.")
    @DisplayName("AN-RULES-09. is_null/is_not_null: параметр не задан")
    void isNullAndIsNotNullShouldHandleAbsentParam() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7335, "AN-RULES-09-IS-NULL", List.of(condition(1, List.of(List.of(rule("STRING", "optionalField", "SPLITTING_OBJECTS", "is_null"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7336, "AN-RULES-09-IS-NOT-NULL", List.of(condition(1, List.of(List.of(rule("STRING", "optionalField", "SPLITTING_OBJECTS", "is_not_null"))))), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest("AN-RULES-09", object(MATCHING_OBJECT_ID,
                param("otherField", "VALUE", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с is_null/is_not_null для отсутствующего параметра", flow -> loadConfigStep(flow, config))
                .step("Проверяем фактический контракт: отсутствующий параметр не матчится ни на is_null, ни на is_not_null", flow -> {
                    var response = shouldBe200(split(flow, request));
                    assertObjectHasAllButNoMainAssignment(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем строковые операторы like, not_like, like_any, not_like_any для STRING.")
    @DisplayName("AN-RULES-06. LIKE-семейство операторов rules для STRING")
    void likeFamilyOperatorsShouldBeCoveredByRestMatrix() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7321, "AN-RULES-06-LIKE", List.of(condition(1, List.of(List.of(rule("STRING", "name", "SPLITTING_OBJECTS", "like", "ALPHA"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7322, "AN-RULES-06-NOT-LIKE", List.of(condition(1, List.of(List.of(rule("STRING", "name", "SPLITTING_OBJECTS", "not_like", "GAMMA"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7323, "AN-RULES-06-LIKE-ANY", List.of(condition(1, List.of(List.of(rule("STRING", "name", "SPLITTING_OBJECTS", "like_any", "ALPHA", "GAMMA"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7324, "AN-RULES-06-NOT-LIKE-ANY", List.of(condition(1, List.of(List.of(rule("STRING", "name", "SPLITTING_OBJECTS", "not_like_any", "GAMMA", "OMEGA"))))), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest("AN-RULES-06", object(MATCHING_OBJECT_ID,
                param("name", "ALPHA", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с LIKE-семейством операторов", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что объект привязался ко всем экспериментам LIKE matrix", flow -> {
                    var response = shouldBe200(split(flow, request));
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 7321L, 7322L, 7323L, 7324L);
                })
                .run();
    }

}
