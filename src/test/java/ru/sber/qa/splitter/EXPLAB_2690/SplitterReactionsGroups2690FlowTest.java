package ru.sber.qa.splitter.EXPLAB_2690;
import steps.flow.splitter.workedgroup.ReactionsGroupsSteps;

import config.environment.EnvironmentConfigurationExample;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import util.support.SplitterVersionProvider;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@Execution(ExecutionMode.SAME_THREAD)
@AnyConfigLoadMode
@Order(30)
@DisplayName("EXPLAB-2690: REACTIONS groups, empty parameters and no-MAIN")
public class SplitterReactionsGroups2690FlowTest extends ReactionsGroupsSteps {

    @CriticalRegression
    @ParameterizedTest(name = "sameCondition={0}, worked={1}")
    @CsvSource({"false,A,0", "false,B,2500", "false,C,5000", "false,NONE,7500",
            "true,A,0", "true,B,2500", "true,C,5000", "true,NONE,7500"})
    void groups(boolean sameCondition, String worked, int from) {
        var conditions = sameCondition ? List.of(objectParamEqualsCondition(1, "segment", "2690", "INTEGER"))
                : List.of(objectParamEqualsCondition(1, "segment", "2690", "INTEGER"),
                objectParamEqualsCondition(2, "segment", "2690", "INTEGER"),
                objectParamEqualsCondition(3, "segment", "2690", "INTEGER"));
        var exp = experiment(269080, SALT_2690, conditions, List.of(
                groupWithDocResult("A", shares(0, 2500), 1, "1", "101"),
                group("B", shares(2500, 5000), List.of(resultWithParams(sameCondition ? 1 : 2,
                        param("actionType", "3", "INTEGER"), param("result", "202", "INTEGER"),
                        new dto.splitter.common.ParamDto("labels", List.of("first", "second"), "STRING")))),
                groupWithEmptyResultParams("C", 5000, 7500, sameCondition ? 1 : 3)));
        long version = SplitterVersionProvider.next();
        var config = configFor(EndpointMode.REACTIONS, version, exp);
        String splittingId = splittingIdForRange("2690-REACTIONS-GROUPS", SALT_2690, from, from + 2500);
        var request = splitRequest(splittingId,
                object(REACTIONS_OBJECT_ID, param("segment", "2690", "INTEGER")),
                object(SINGLE_OBJECT_ID, param("segment", "unlinked", "STRING")));
        getFlowWithRest()
                .step("Load REACTIONS A/B/C configuration", flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Verify exact independent object results", flow -> {
                    var response = split(flow, EndpointMode.REACTIONS, request);
                    assertAll("REACTIONS objects", () -> assertBasicResponseContract(response, request, version), () -> {
                        if (worked.equals("NONE")) assertObjectWithoutMain(response, REACTIONS_OBJECT_ID);
                        else {
                            assertRulesExactly(response, REACTIONS_OBJECT_ID, "MAIN", "ALL");
                            for (String rule : List.of("MAIN", "ALL")) {
                                assertRuleResultSize(response, REACTIONS_OBJECT_ID, rule, 1);
                                var result = firstRuleExp(response, REACTIONS_OBJECT_ID, rule);
                                assertEquals(269080L, result.path("expId").longValue());
                                assertEquals(worked, result.path("expGroup").textValue());
                                assertEquals(worked, result.path("finalExpGroup").textValue());
                                assertEquals(sameCondition ? 1 : worked.charAt(0) - 'A' + 1, result.path("conditionId").intValue());
                                assertConfiguredExperiment(response, result);
                            }
                            assertMainHasNoExpFlags(response, REACTIONS_OBJECT_ID);
                            assertAllExpFlagsHaveAlternativeValue(response, REACTIONS_OBJECT_ID, "false");
                        }
                    }, () -> assertObjectHasStrictlyEmptyResult(response, SINGLE_OBJECT_ID),
                            () -> assertNoAlternativeTrueAnywhere(response));
                }).run();
    }
}
