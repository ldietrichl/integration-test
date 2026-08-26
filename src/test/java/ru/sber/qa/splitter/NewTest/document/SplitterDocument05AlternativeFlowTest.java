package ru.sber.qa.splitter.NewTest.document;

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
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;
import ru.sber.qa.allure.CriticalRegression;

import java.util.List;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
/**
 * Сценарии этого класса зависят от ConfigMap splitter-mapper-service-lib / splitter-rules-mapper.yml:
 * - traffic-based-alternative=false;
 * - rules.alternative-markup-rule описывает alt-markup/rollback значения, но traffic-based механизм выключен.
 * На текущем стенде ожидаем isAlternative=false. Если traffic-based-alternative включат,
 * ожидания в assertAnyExpHasAlternativeFlag(...) нужно пересогласовать.
 */
public class SplitterDocument05AlternativeFlowTest extends AbstractSplitterDocumentFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("DOC-05-01. базовая альтернатива: неальтернативный MAIN помечается isAlternative=false")
    void mainExperimentWithoutAlternativeShouldHaveAlternativeFlagFalse() {
        // ConfigMap-dependent: на стенде traffic-based-alternative=false,
        // поэтому в ALL ожидаем expFlags.isAlternative=false.
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(10501, "DOC-05-01-SALT-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "1"))),
                experiment(10502, "DOC-05-01-SALT-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "1"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем два обычных связанных эксперимента без условий альтернативы", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что MAIN выбран по expId, а isAlternative=false по ConfigMap traffic-based-alternative=false", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10501L);
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 10501L, 10502L);
                    assertAnyExpHasAlternativeFlag(response, MATCHING_OBJECT_ID, 10501L, "false");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DOC-05-02. пример альтернативы: при выключенной traffic-based-alternative isAlternative остается false")
    void objectShouldNotBeMarkedAsAlternativeWhenTrafficBasedAlternativeIsDisabled() {
        // ConfigMap-dependent: traffic-based-alternative=false выключает traffic-based alternative markup,
        // поэтому даже сценарий из раздела альтернатив должен возвращать isAlternative=false.
        long version = SplitterVersionProvider.next();
        String splittingId = "4000";
        ObjectSelectConditionDto exp1Condition1 = objectParamEqualsCondition(1, "cjId", "1", "INTEGER");
        ObjectSelectConditionDto exp1Condition2 = objectParamEqualsCondition(2, "cjId", "2", "INTEGER");
        ObjectSelectConditionDto exp2Condition = objectParamInCondition(1, "cjId", "INTEGER", "1", "2");

        LoadConfigRequestDto config = config(version,
                experiment(10503, "24096d2M1e", List.of(exp1Condition1, exp1Condition2), List.of(
                        group("A", 0, 5000, 1, "0"),
                        group("B", 5000, 10000, 2, "1"))),
                experiment(10504, "2409w13Bw6", List.of(exp2Condition), List.of(
                        group("A", 0, 5000, 1, "1"),
                        group("B", 5000, 10000, 1, "0"))));
        SplitRequestDto request = splitRequest(splittingId,
                object(MATCHING_OBJECT_ID, param("cjId", "1", "INTEGER")),
                object(SECOND_OBJECT_ID, param("cjId", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг из раздела альтернатив с двумя объектами", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что при ConfigMap traffic-based-alternative=false оба объекта имеют isAlternative=false", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10503L);
                    assertAnyExpHasAlternativeFlag(response, MATCHING_OBJECT_ID, 10503L, "false");
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 10503L, 10504L);
                    assertObjectHasMainAndAll(response, SECOND_OBJECT_ID);
                    assertMainExp(response, SECOND_OBJECT_ID, 10503L);
                    assertAnyExpHasAlternativeFlag(response, SECOND_OBJECT_ID, 10503L, "false");
                })
                .run();
    }
}
