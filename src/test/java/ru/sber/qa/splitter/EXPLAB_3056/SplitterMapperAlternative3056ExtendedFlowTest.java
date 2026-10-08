package ru.sber.qa.splitter.EXPLAB_3056;

import config.environment.EnvironmentConfigurationExample;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import steps.flow.splitter.workedgroup.MapperAlternativeMarkupSteps;
import support.splitter.cases.MapperAlternativeMarkupCases;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@Execution(ExecutionMode.SAME_THREAD)
@AnyConfigLoadMode
@Epic("Splitter") @Feature("EXPLAB-3056")
@DisplayName("EXPLAB-3056 MAPPER: расширенные правила и предрасчёт")
public class SplitterMapperAlternative3056ExtendedFlowTest extends MapperAlternativeMarkupSteps {
    @ParameterizedTest(name="{0}") @ScenarioProfile(Profile.ALT_PARAM)
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#alternateParameter")
    void parameterCodeIsConfigurable(Case scenario, KafkaService kafka) { verify(scenario,kafka); }

    @ParameterizedTest(name="{0}") @ScenarioProfile(Profile.REVERSED_MARKUP)
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#reversedMarkup")
    void markupValuesOrderIsIrrelevant(Case scenario, KafkaService kafka) { verify(scenario,kafka); }

    @ParameterizedTest(name="{0}") @ScenarioProfile(Profile.ROLLBACK_SIX)
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#boundaries")
    void rangeBoundariesAndNone(Case scenario, KafkaService kafka) { verify(scenario,kafka); }

    @ParameterizedTest(name="{0}") @ScenarioProfile(Profile.ROLLBACK_SIX)
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#multipleLinkedGroups")
    void everyLinkedRowGetsExperimentFlag(Case scenario, KafkaService kafka) { verifyPermutation(scenario,kafka); }

    @ParameterizedTest(name="{0}") @ScenarioProfile(Profile.WORKING)
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#workingRules")
    void workingListsAndFiltering(Case scenario, KafkaService kafka) { verify(scenario,kafka); }

    @ParameterizedTest(name="{0}") @ScenarioProfile(Profile.FILTER_DISABLED)
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#filterControls")
    void disabledFilterPreservesMarkup(Case scenario, KafkaService kafka) { verify(scenario,kafka); }

    @Disabled("Вне объёма EXPLAB-3056: независимая настройка подавления альтернатив; SDK не поддерживает filter-alternative.")
    @ParameterizedTest(name="{0}")
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#filterControls")
    void alternativeFilterIsIndependentOfParameterFilter(Case scenario, KafkaService kafka) {
        Assertions.fail("Independent filter configuration is outside EXPLAB-3056; do not replace it with filter-rule.enabled");
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#exactGroupPair")
    void rollbackUsesExactGroupPair(Case scenario, KafkaService kafka) { verifyPermutation(scenario,kafka); }

    @Test @DisplayName("3056-T20: прямая разметка, откат, отсутствие кандидата на одной версии")
    void fullMarkupResetSequence(KafkaService kafka) { verifySequence(MapperAlternativeMarkupCases.fullFlagSequence(),kafka,false); }

    @ParameterizedTest(name="3056-T29 PRECALC / {0}") @ScenarioProfile(Profile.PRECALC)
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#core")
    void precalculatedLinksMatchDynamicContract(Case scenario, KafkaService kafka) { verify(scenario,kafka); }

    @Disabled("Вне объёма EXPLAB-3056: обновление таблицы предрасчёта относится к регрессии предрасчёта 2885/2891.")
    @Test
    @DisplayName("3056-T29/T21: предрасчёт обновляет связи после новой конфигурации")
    void precalculatedLinksFollowVersion(KafkaService kafka) {
        Assertions.fail("Cache refresh is outside EXPLAB-3056; T29 checks markup on freshly calculated links");
    }

    @Disabled("Нет контракта Q2/Q3; traffic=false противоречиво описан. См. DOCUMENTATION_DECISIONS.txt; не засчитывать покрытием.")
    @ParameterizedTest(name="BLOCKED / {0}")
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#unresolvedContracts")
    void unresolvedContract(MapperAlternativeMarkupCases.ContractGap gap) {
        Assertions.fail("Define a documented expected result before enabling: "+gap);
    }
}
