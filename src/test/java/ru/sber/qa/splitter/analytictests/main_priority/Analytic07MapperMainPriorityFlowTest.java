package ru.sber.qa.splitter.analytictests.main_priority;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
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

import java.util.ArrayList;
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
public class Analytic07MapperMainPriorityFlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверяем все actionType; побеждать должен эксперимент с максимальным приоритетом actionType.")
    @DisplayName("AN-MAIN-01. MAIN выбирается по максимальному приоритету actionType из неподавляемого набора")
    void mainShouldUseHighestActionTypePriorityFromReturnedObject() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                priorityExperiment(7701, "0"),
                priorityExperiment(7702, "1"),
                priorityExperiment(7703, "3"),
                priorityExperiment(7704, "5"),
                priorityExperiment(7705, "6"));
        SplitRequestDto split = prioritySplit("AN-MAIN-01");

        getFlowWithRest()
                .step("Загружаем config с actionType 0/1/3/5/6", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что MAIN выбран по максимальному priority-map текущей ConfigMap", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7703L);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "3");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Привязано несколько экспериментов с одинаковыми actionType; побеждает эксперимент с минимальным id.")
    @DisplayName("AN-MAIN-02. При одинаковом actionType побеждает меньший expId")
    void sameActionTypeShouldChooseMinimalExpId() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                priorityExperiment(7712, "5"),
                priorityExperiment(7711, "5"));
        SplitRequestDto split = prioritySplit("AN-MAIN-02");

        getFlowWithRest()
                .step("Загружаем config с одинаковым actionType и обратным порядком expId", flow -> loadConfigStep(flow, config))
                .step("Проверяем tie-break: минимальный expId", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertMainExp(response, MATCHING_OBJECT_ID, 7711L);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "5");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: В несработавших группах максимальный приоритет actionType; побеждает только эксперимент по сработавшей группе.")
    @DisplayName("AN-MAIN-03. Несработавшая группа с более высоким actionType не должна становиться MAIN")
    void nonMatchedGroupWithHigherActionTypeShouldNotWinMain() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7721, "AN-MAIN-03", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(
                        group("A", 0, 10000, 1, "1"),
                        group("B", 10001, 10002, 1, "6"))));
        SplitRequestDto split = splitRequest("AN-MAIN-03", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config, где несработавшая группа имеет более высокий actionType", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что MAIN берется только из сработавшей группы", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertMainExp(response, MATCHING_OBJECT_ID, 7721L);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "1");
                })
                .run();
    }


    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Проверки делаем с несколькими condition в группе; результат MAIN должен быть один.")
    @DisplayName("AN-MAIN-04. Несколько conditions в одной группе дают один MAIN result")
    void manyConditionsInOneGroupShouldStillReturnSingleMainResult() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7722, "AN-MAIN-04",
                        List.of(
                                objectParamEqualsCondition(1, "segment", "A", "STRING"),
                                objectParamEqualsCondition(2, "segment", "B", "STRING")),
                        List.of(group("A", List.of(share(0, 10000)), List.of(
                                result(1, "0"),
                                result(2, "1"))))));
        SplitRequestDto split = splitRequest("AN-MAIN-04", object(MATCHING_OBJECT_ID, param("segment", "B", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с двумя conditions, привязанными к одной группе", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что MAIN содержит один resultExp и корректный conditionId", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertRuleResultSize(response, MATCHING_OBJECT_ID, "MAIN", 1);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7722L);
                    assertMainConditionId(response, MATCHING_OBJECT_ID, 2);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "1");
                })
                .run();
    }


    private ExperimentDto priorityExperiment(int id, String actionType) {
        return experiment(id, "AN-MAIN-SALT", List.of(objectParamEqualsCondition(1, "channelId", "20", "INTEGER")), List.of(fullRangeGroup("A", 1, actionType)));
    }

    private SplitRequestDto prioritySplit(String splittingId) {
        return splitRequest(splittingId, object(MATCHING_OBJECT_ID, param("channelId", "20", "INTEGER")));
    }
}
