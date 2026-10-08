package ru.sber.qa.splitter.extension;

import steps.flow.splitter.workedgroup.WorkedGroupSteps;

import config.environment.EnvironmentConfigurationExample;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import util.support.SplitterVersionProvider;
import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@Execution(ExecutionMode.SAME_THREAD)
@AnyConfigLoadMode
@Order(30)
@DisplayName("Splitter extension: Tests-v10 scenario 3, six experiments and three layers")
public class ReactionsLayerPriorityFlowTest extends WorkedGroupSteps {
    @Override protected EndpointMode endpointMode() { return EndpointMode.REACTIONS; }

    @CriticalRegression
    @ParameterizedTest(name = "spreadFrom={0}")
    @ValueSource(ints = {0, 2500, 5000, 7500})
    void layers(int from) {
        long version = SplitterVersionProvider.next();
        int[] priorities = {1, 2, 2, 3, 3, 3};
        int[] ends = {2500, 7500, 2500, 5000, 7500, 5000};
        var experiments = new dto.splitter.config.ExperimentDto[6];
        for (int i = 0; i < 6; i++) experiments[i] = layeredExperiment(269101 + i, SALT_2690,
                priorities[i], priorities[i], List.of(objectParamEqualsCondition(1, "segment", "2690", "INTEGER")),
                List.of(groupWithDocResult("A", shares(0, ends[i]), 1, "1", String.valueOf(i + 1))));
        var config = configFor(EndpointMode.REACTIONS, version, experiments);
        var request = splitRequest(splittingIdForRange("2690-LAYERS", SALT_2690, from, from + 2500),
                object(REACTIONS_OBJECT_ID, param("segment", "2690", "INTEGER")));
        Long[] all = switch (from) {
            case 0 -> ids(269101, 269102, 269103, 269104, 269105, 269106);
            case 2500 -> ids(269102, 269104, 269105, 269106);
            case 5000 -> ids(269102, 269105);
            default -> ids();
        };
        Long[] main = from < 5000 ? ids(269104, 269105, 269106) : from == 5000 ? ids(269105) : ids();
        getFlowWithRest().step("Load six experiments from Tests-v10", flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Check exact MAIN and ALL experiment sets", flow -> {
                    var response = split(flow, EndpointMode.REACTIONS, request);
                    assertBasicResponseContract(response, request, version);
                    if (main.length == 0) assertObjectWithoutMain(response, REACTIONS_OBJECT_ID);
                    else {
                        assertRulesExactly(response, REACTIONS_OBJECT_ID, "MAIN", "ALL");
                        assertRuleExpIdsExactly(response, REACTIONS_OBJECT_ID, "MAIN", main);
                        assertRuleExpIdsExactly(response, REACTIONS_OBJECT_ID, "ALL", all);
                        for (String rule : List.of("MAIN", "ALL")) {
                            assertEveryRuleExpUsesWorkedGroup(response, REACTIONS_OBJECT_ID, rule);
                            for (var exp : findRule(response, REACTIONS_OBJECT_ID, rule, true).path("resultExps"))
                                assertConfiguredExperiment(response, exp);
                        }
                        assertMainHasNoExpFlags(response, REACTIONS_OBJECT_ID);
                        assertAllExpFlagsHaveAlternativeValue(response, REACTIONS_OBJECT_ID, "false");
                    }
                    assertNoAlternativeTrueAnywhere(response);
                }).run();
    }
}
