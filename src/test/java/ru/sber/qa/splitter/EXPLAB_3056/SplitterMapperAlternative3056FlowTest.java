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
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import steps.flow.splitter.workedgroup.MapperAlternativeMarkupSteps;
import support.splitter.cases.MapperAlternativeMarkupCases;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@Execution(ExecutionMode.SAME_THREAD)
@AnyConfigLoadMode
@Epic("Splitter")
@Feature("EXPLAB-3056")
@DisplayName("EXPLAB-3056 MAPPER: связи групп, разметка и откат альтернатив")
public class SplitterMapperAlternative3056FlowTest extends MapperAlternativeMarkupSteps {
    @CriticalRegression
    @ParameterizedTest(name="{0}")
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#core")
    void linkedGroupsAndRollback(Case scenario, KafkaService kafka) { verify(scenario,kafka); }

    @ParameterizedTest(name="3056-T18 / {0}")
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#permutations")
    void orderAndRepeatedRequests(Case scenario, KafkaService kafka) { verifyPermutation(scenario,kafka); }

    @Test @DisplayName("3056-T19: откат ограничен объектами текущего запроса")
    void rollbackRequestScope(KafkaService kafka) { verifySequence(MapperAlternativeMarkupCases.requestScope(),kafka,false); }

    @Test @DisplayName("3056-T20: флаги сбрасываются между распределениями")
    void rollbackDoesNotLeak(KafkaService kafka) { verifySequence(MapperAlternativeMarkupCases.resetFlags(),kafka,false); }

    @Test @DisplayName("3056-T21: изменение и восстановление связей новой версией")
    void linksFollowConfigVersion(KafkaService kafka) { verifySequence(MapperAlternativeMarkupCases.changeLinks(),kafka,false); }
}
