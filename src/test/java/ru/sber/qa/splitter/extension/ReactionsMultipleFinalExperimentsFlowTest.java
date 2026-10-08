package ru.sber.qa.splitter.extension;

import steps.flow.splitter.workedgroup.WorkedGroupSteps;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
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
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("Splitter extension. REACTIONS: итоговый эксперимент по layerPriority и expId")
@AnyConfigLoadMode
@org.junit.jupiter.api.Order(30)
public class ReactionsMultipleFinalExperimentsFlowTest extends WorkedGroupSteps {

    @Override protected EndpointMode endpointMode() { return EndpointMode.REACTIONS; }

    @CriticalRegression
    @Test
    @DisplayName("Splitter extension-07. MAIN содержит все сработавшие эксперименты максимального layerPriority")
    void reactionsMainShouldContainSelectedExperimentOfMaximumLayerPriority() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.REACTIONS, version,
                reactionExperiment(269071, 1, 1, "101"),
                reactionExperiment(269072, 2, 3, "202"),
                reactionExperiment(269073, 3, 3, "303"),
                reactionExperiment(269074, 4, 3, "404"));
        SplitRequestDto request = splitRequest("EXPLAB-2690-REACTIONS-LAYERS-" + version,
                object(REACTIONS_OBJECT_ID, param("segment", "2690", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем REACTIONS config: один exp с priority=1 и три exp с priority=3",
                        flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Проверяем полный набор MAIN максимального приоритета по документу Тесты-v10",
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.REACTIONS, request);
                            assertBasicResponseContract(response, request, version);
                            assertRuleExpIdsExactly(response, REACTIONS_OBJECT_ID, "MAIN", 269072L, 269073L, 269074L);
                            assertRulesExactly(response, REACTIONS_OBJECT_ID, "MAIN", "ALL");
                            assertRuleExpsHaveMandatoryFields(response, REACTIONS_OBJECT_ID, "MAIN");
                            assertEveryRuleExpUsesWorkedGroup(response, REACTIONS_OBJECT_ID, "MAIN");
                            assertMainHasNoExpFlags(response, REACTIONS_OBJECT_ID);
                                assertRuleExpIdsExactly(response, REACTIONS_OBJECT_ID, "ALL",
                                        269071L, 269072L, 269073L, 269074L);
                                assertEveryRuleExpUsesWorkedGroup(response, REACTIONS_OBJECT_ID, "ALL");
                                assertAllExpFlagsHaveAlternativeValue(response, REACTIONS_OBJECT_ID, "false");
                            for (String rule : List.of("MAIN", "ALL"))
                                for (var exp : findRule(response, REACTIONS_OBJECT_ID, rule, true).path("resultExps"))
                                    assertConfiguredExperiment(response, exp);
                            assertNoAlternativeTrueAnywhere(response);
                        })
                .run();
    }

    private ExperimentDto reactionExperiment(int expId, int layerId, int layerPriority, String resultValue) {
        return layeredExperiment(expId,
                SALT_2690 + "-" + expId,
                layerId,
                layerPriority,
                List.of(objectParamEqualsCondition(1, "segment", "2690", "INTEGER")),
                List.of(groupWithDocResult("A", shares(0, 10000), 1, "1", resultValue)));
    }
}
