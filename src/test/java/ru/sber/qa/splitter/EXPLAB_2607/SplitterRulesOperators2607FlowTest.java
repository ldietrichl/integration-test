package ru.sber.qa.splitter.EXPLAB_2607;

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
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;
import util.support.SplitterVersionProvider;

import java.util.List;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
public class SplitterRulesOperators2607FlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("AN-RULES-04. Матрица базовых операторов rules для INTEGER/STRING: EXPLAB-2607 проверяет less, more, less_equal, more_equal и is_null")
    @DisplayName("EXPLAB-2607 / AN-RULES-04. less/more/less_equal/more_equal и is_null корректно привязывают эксперимент к объекту")
    void basicIntegerStringOperatorsShouldLinkExperimentsToSingleObjectExceptAbsentIsNull() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(260701, "EXPLAB-2607-EQ", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "equal", "10"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(260702, "EXPLAB-2607-NE", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "not_equal", "5"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(260703, "EXPLAB-2607-MORE", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "more", "5"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(260704, "EXPLAB-2607-LESS", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "less", "15"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(260705, "EXPLAB-2607-MORE-EQ", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "more_equal", "10"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(260706, "EXPLAB-2607-LESS-EQ", List.of(condition(1, List.of(List.of(rule("INTEGER", "score", "SPLITTING_OBJECTS", "less_equal", "10"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(260707, "EXPLAB-2607-IN", List.of(condition(1, List.of(List.of(rule("STRING", "status", "SPLITTING_OBJECTS", "in", "OK", "WARN"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(260708, "EXPLAB-2607-NOT-IN", List.of(condition(1, List.of(List.of(rule("STRING", "status", "SPLITTING_OBJECTS", "not_in", "FAIL", "BLOCKED"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(260709, "EXPLAB-2607-IS-NULL", List.of(condition(1, List.of(List.of(rule("STRING", "optionalField", "SPLITTING_OBJECTS", "is_null"))))), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(260710, "EXPLAB-2607-IS-NOT-NULL", List.of(condition(1, List.of(List.of(rule("STRING", "status", "SPLITTING_OBJECTS", "is_not_null"))))), List.of(fullRangeGroup("A", 1, "0"))));

        SplitRequestDto absentNullParamRequest = splitRequest("EXPLAB-2607-ABSENT-NULL", object(MATCHING_OBJECT_ID,
                param("score", "10", "INTEGER"),
                param("status", "OK", "STRING")));
        SplitRequestDto explicitNullParamRequest = splitRequest("EXPLAB-2607-EXPLICIT-NULL", object(MATCHING_OBJECT_ID,
                param("score", "10", "INTEGER"),
                param("status", "OK", "STRING"),
                paramWithNullValue("optionalField", "STRING")));

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2607: 10 экспериментов, каждый проверяет отдельный оператор rules", flow -> loadConfigStep(flow, config))
                .step("Параметр optionalField отсутствует: должны привязаться все эксперименты, кроме is_null", flow -> {
                    var response = shouldBe200(split(flow, absentNullParamRequest));
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID,
                            260701L, 260702L, 260703L, 260704L, 260705L,
                            260706L, 260707L, 260708L, 260710L);
                })
                .step("Параметр optionalField явно передан как null: должен дополнительно привязаться is_null", flow -> {
                    var response = shouldBe200(split(flow, explicitNullParamRequest));
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID,
                            260701L, 260702L, 260703L, 260704L, 260705L,
                            260706L, 260707L, 260708L, 260709L, 260710L);
                })
                .run();
    }
}
