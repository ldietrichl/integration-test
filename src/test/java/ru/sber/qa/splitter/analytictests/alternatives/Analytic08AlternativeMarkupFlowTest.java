package ru.sber.qa.splitter.analytictests.alternatives;


import ru.sber.qa.allure.ManualTest;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
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
public class Analytic08AlternativeMarkupFlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Эксперимент привязан к объекту несколькими группами; альтернатива не должна срабатывать.")
    @DisplayName("AN-ALT-01. При traffic-based-alternative=true два обычных связанных exp на одном объекте не размечаются альтернативой")
    void ordinaryLinkedExperimentsOnSameObjectShouldNotBecomeAlternative() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7801, "AN-ALT-01-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "1"))),
                experiment(7802, "AN-ALT-01-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "1"))));
        SplitRequestDto split = splitRequest("AN-ALT-01", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем два обычных связанных эксперимента при traffic-based-alternative=true", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что обычные exp на том же объекте не размечены как alternative", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 7801L, 7802L);
                    assertAnyExpHasAlternativeFlag(response, MATCHING_OBJECT_ID, 7801L, "false");
                    assertAnyExpHasAlternativeFlag(response, MATCHING_OBJECT_ID, 7802L, "false");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Эксперимент привязан к одному объекту сразу несколькими группами; группа, привязанная к тому же объекту, не должна размечаться альтернативой.")
    @DisplayName("AN-ALT-02. Связанные группы на одном объекте не размечаются альтернативой")
    void documentedAlternativeScenarioShouldStayFalseOnCurrentConfigMap() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto exp1Condition1 = objectParamEqualsCondition(1, "cjId", "1", "INTEGER");
        ObjectSelectConditionDto exp1Condition2 = objectParamEqualsCondition(2, "cjId", "2", "INTEGER");
        ObjectSelectConditionDto exp2Condition = objectParamInCondition(1, "cjId", "INTEGER", "1", "2");
        LoadConfigRequestDto config = config(version,
                experiment(7803, "24096d2M1e", List.of(exp1Condition1, exp1Condition2), List.of(group("A", 0, 5000, 1, "0"), group("B", 5000, 10000, 2, "1"))),
                experiment(7804, "2409w13Bw6", List.of(exp2Condition), List.of(group("A", 0, 5000, 1, "1"), group("B", 5000, 10000, 1, "0"))));
        SplitRequestDto split = splitRequest("AN-ALT-02", object(MATCHING_OBJECT_ID, param("cjId", "1", "INTEGER")), object(SECOND_OBJECT_ID, param("cjId", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config из раздела альтернатив", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что связанные группы на тех же объектах не становятся alternative", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertAnyExpHasAlternativeFlag(response, MATCHING_OBJECT_ID, 7803L, "false");
                    assertAnyExpHasAlternativeFlag(response, MATCHING_OBJECT_ID, 7804L, "false");
                    assertAnyExpHasAlternativeFlag(response, SECOND_OBJECT_ID, 7803L, "false");
                    assertAnyExpHasAlternativeFlag(response, SECOND_OBJECT_ID, 7804L, "false");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Простейший A/B: один эксперимент, два объекта, группа A привязана к первому объекту, группа B ко второму; объект из несработавшей группы размечается альтернативой.")
    @DisplayName("AN-ALT-04. Альтернативный MAIN использует группу связи объекта и отдельно finalExpGroup")
    void oneExperimentTwoObjectsShouldMarkOtherObjectAsAlternative() {
        long version = SplitterVersionProvider.next();
        String salt = "AN-ALT-04-SALT";
        String splitForA = splittingIdForExactSpread("AN-ALT-04-A-SP1000", salt, 1000);
        ObjectSelectConditionDto objectOneCondition = objectParamEqualsCondition(1, "cjId", "1", "INTEGER");
        ObjectSelectConditionDto objectTwoCondition = objectParamEqualsCondition(2, "cjId", "2", "INTEGER");
        LoadConfigRequestDto config = config(version,
                experiment(7805, salt, List.of(objectOneCondition, objectTwoCondition), List.of(
                        group("A", 0, 5000, 1, "0"),
                        group("B", 5000, 10000, 2, "0"))));
        SplitRequestDto split = splitRequest(splitForA,
                object(MATCHING_OBJECT_ID, param("cjId", "1", "INTEGER")),
                object(SECOND_OBJECT_ID, param("cjId", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config: один эксперимент, group A привязана к object1, group B к object2", flow -> loadConfigStep(flow, config))
                .step("Проверяем REST-контракт альтернативного MAIN по EXPLAB-2690", flow -> {
                    var response = shouldBe200(split(flow, split));

                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 1000);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                    assertMainFinalGroup(response, MATCHING_OBJECT_ID, "A");
                    assertMainConditionId(response, MATCHING_OBJECT_ID, 1);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                    assertMainExpFlagsEmpty(response, MATCHING_OBJECT_ID);
                    assertAllExpAlternativeFlag(response, MATCHING_OBJECT_ID, 7805L, 1, "A", "false");

                    // Объект 2 связан с экспериментом через B, а фактически сработала A.
                    // В MAIN возвращаем B/resultParams(B), finalExpGroup=A; альтернативная строка удаляется из REST ALL.
                    assertMainExp(response, SECOND_OBJECT_ID, 7805L);
                    assertMainGroup(response, SECOND_OBJECT_ID, "B");
                    assertMainFinalGroup(response, SECOND_OBJECT_ID, "A");
                    assertMainConditionId(response, SECOND_OBJECT_ID, 2);
                    assertMainActionType(response, SECOND_OBJECT_ID, "0");
                    assertMainExpFlagsEmpty(response, SECOND_OBJECT_ID);
                    assertRuleAbsent(response, SECOND_OBJECT_ID, "ALL");
                })
                .run();
    }

    @Disabled("Нужна управляемая ConfigMap: alt-markup-values/alt-rollback-values нельзя менять из текущего REST flow")
    @ManualTest
    @Test
    @AnalyticTag("Аналитика: Меняем список actionType для alternative markup и rollback, перепроверяем результат.")
    @DisplayName("AN-ALT-03. Alternative markup и rollback при разных списках АТ")
    void alternativeMarkupAndRollbackShouldBeCoveredWithConfigMapMatrix() {
    }
}
