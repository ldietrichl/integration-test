package ru.sber.qa.splitter.EXPLAB_2835;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.config.RuleDto;
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
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;

@CriticalRegression
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2835. Split: empty rules")
public class SplitterEmptyRules2835FlowTest extends AbstractSplitterV9FlowTest {

    private static final long EMPTY_RULES_EXP_ID = 283501L;
    private static final long IN_EMPTY_VALUES_EXP_ID = 283502L;
    private static final long IN_ONE_VALUE_EXP_ID = 283503L;
    private static final String SALT = "EXPLAB-2835-SALT";
    private static final String OBJECT_EMPTY_RULES = "explab-2835-empty-rules-object";
    private static final String OBJECT_NOT_MATCHED = "explab-2835-in-empty-not-matched";
    private static final String OBJECT_MATCHED = "explab-2835-in-one-matched";

    @Test
    @DisplayName("EXPLAB-2835-SPL-04. rules=[] привязывает любой объект при выполнении split")
    void splitShouldMatchEveryObjectWhenObjectSelectConditionRulesIsEmpty() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                emptyRulesExperiment());

        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-04",
                objectWithUniqueId("explab-2835-empty-rules-uid",
                        OBJECT_EMPTY_RULES,
                        param("anyParam", "does-not-matter", "STRING")));

        getFlowWithRest()
                .step("Загружаем MAPPER config с objectSelectConditions[].rules=[]", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split: объект должен привязаться без проверки параметров", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertSplittingResultsHaveUniqueObjectIds(response);
                    assertRuleResultSize(response, OBJECT_EMPTY_RULES, "MAIN", 1);
                    assertFirstRuleExp(response, OBJECT_EMPTY_RULES, "MAIN", EMPTY_RULES_EXP_ID, "A", "A");
                    assertFirstRuleExpConditionId(response, OBJECT_EMPTY_RULES, "MAIN", 1);
                    assertRuleExpIdsExactly(response, OBJECT_EMPTY_RULES, "ALL", EMPTY_RULES_EXP_ID);
                    assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-05. rules=[[param in []]] не равно rules=[] и не матчит объект")
    void splitShouldNotTreatInEmptyValuesRuleAsEmptyRules() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                inEmptyValuesExperiment(),
                inOneValueExperiment());

        SplitRequestDto notMatchedRequest = splitRequest("EXPLAB-2835-SPL-05-NOT-MATCHED",
                objectWithUniqueId("explab-2835-in-empty-not-matched-uid",
                        OBJECT_NOT_MATCHED,
                        param("configCommId", "2", "INTEGER")));
        SplitRequestDto matchedRequest = splitRequest("EXPLAB-2835-SPL-05-MATCHED",
                objectWithUniqueId("explab-2835-in-one-matched-uid",
                        OBJECT_MATCHED,
                        param("configCommId", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config: expId=283502 с values=[] и expId=283503 с values=[1]", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Split для configCommId=2 не должен выбрать experiment с values=[]", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, notMatchedRequest);
                    assertBasicResponseContract(response, notMatchedRequest, version);
                    assertObjectEmptyOrAbsent(response, OBJECT_NOT_MATCHED);
                    assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                })
                .step("Split для configCommId=1 должен выбрать только experiment с values=[1]", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, matchedRequest);
                    assertBasicResponseContract(response, matchedRequest, version);
                    assertRuleResultSize(response, OBJECT_MATCHED, "MAIN", 1);
                    assertFirstRuleExp(response, OBJECT_MATCHED, "MAIN", IN_ONE_VALUE_EXP_ID, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT_MATCHED, "MAIN", IN_ONE_VALUE_EXP_ID);
                    assertRuleExpIdsExactly(response, OBJECT_MATCHED, "ALL", IN_ONE_VALUE_EXP_ID);
                    assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                })
                .run();
    }

    private ExperimentDto emptyRulesExperiment() {
        return experiment((int) EMPTY_RULES_EXP_ID,
                SALT,
                List.of(condition(1, List.of())),
                List.of(fullRangeActionTypeZeroGroup()));
    }

    private ExperimentDto inEmptyValuesExperiment() {
        return inValuesExperiment((int) IN_EMPTY_VALUES_EXP_ID, List.of());
    }

    private ExperimentDto inOneValueExperiment() {
        return inValuesExperiment((int) IN_ONE_VALUE_EXP_ID, List.of("1"));
    }

    private ExperimentDto inValuesExperiment(int id, List<String> values) {
        RuleDto rule = RuleDto.builder()
                .dataType("INTEGER")
                .paramCode("configCommId")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("in")
                .values(values)
                .build();
        ObjectSelectConditionDto condition = condition(1, List.of(List.of(rule)));
        return experiment(id,
                SALT,
                List.of(condition),
                List.of(fullRangeActionTypeZeroGroup()));
    }

    private GroupDto fullRangeActionTypeZeroGroup() {
        return group("A",
                List.of(share(0, 10000)),
                List.of(resultWithParams(1, param("actionType", "0", "INTEGER"))));
    }
}
