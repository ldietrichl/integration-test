package ru.sber.qa.splitter.tests_v9.empty_rules;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
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
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("Tests-v9. MAPPER: experiment with empty rules + many split requests")
@AnyConfigLoadMode
public class SplitterV9EmptyRulesManySplitsFlowTest extends AbstractSplitterV9FlowTest {

    private static final int REQUEST_COUNT = 100;
    private static final long EMPTY_RULES_EXP_ID = 102430L;
    private static final long NON_EMPTY_RULES_EXP_ID = 102431L;
    private static final String EMPTY_RULES_SALT = "2406GsjJBv";
    private static final String OBJECT_PREFIX = "v9-empty-rules-object-";

    @Test
    @DisplayName("SPL-V9-EMPTY-RULES-01. Два эксперимента: empty rules + non-empty rules, серия split-запросов")
    void mapperConfigWithEmptyRulesExperimentAndNonEmptyRulesExperimentShouldHandleManySplitRequests() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                emptyRulesExperiment(),
                nonEmptyRulesExperiment());

        getFlowWithRest()
                .step("Загружаем MAPPER config с двумя экспериментами: один с rules=[], второй с непустым rules",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем серию split-запросов после загрузки config: count=" + REQUEST_COUNT,
                        flow -> {
                            for (int index = 0; index < REQUEST_COUNT; index++) {
                                String objectId = OBJECT_PREFIX + index;
                                SplitRequestDto request = splitRequest(
                                        "SPL-V9-EMPTY-RULES-01-" + index,
                                        objectWithUniqueId(String.valueOf(54000 + index),
                                                objectId,
                                                param("configCommId", String.valueOf(54000 + index), "INTEGER")));

                                ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                                //assertBasicResponseContract(response, request, version);
                                //assertSplittingResultsHaveUniqueObjectIds(response);
                               // assertEmptyRulesExperimentSelected(response, objectId);
                                //assertNonEmptyRulesExperimentNotSelected(response, objectId);
                                //assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                            }
                        })
                .run();
    }

    private ExperimentDto emptyRulesExperiment() {
        return experiment((int) EMPTY_RULES_EXP_ID,
                EMPTY_RULES_SALT,
                List.of(emptyRulesCondition()),
                List.of(fullRangeActionTypeZeroGroup()));
    }

    /**
     * Структурно непустой rules по примеру постановки: rule присутствует, values пустой.
     */
    private ExperimentDto nonEmptyRulesExperiment() {
        return experiment((int) NON_EMPTY_RULES_EXP_ID,
                EMPTY_RULES_SALT,
                List.of(configCommIdInEmptyValuesCondition()),
                List.of(fullRangeActionTypeZeroGroup()));
    }

    private ObjectSelectConditionDto emptyRulesCondition() {
        return condition(1, List.of());
    }

    private ObjectSelectConditionDto configCommIdInEmptyValuesCondition() {
        RuleDto rule = rule("INTEGER", "configCommId", "SPLITTING_OBJECTS", "in");
        return condition(1, List.of(List.of(rule)));
    }

    private GroupDto fullRangeActionTypeZeroGroup() {
        return group("A",
                List.of(share(0, 10000)),
                List.of(resultWithParams(1, param("actionType", "0", "INTEGER"))));
    }

    private void assertEmptyRulesExperimentSelected(ValidatableResponseWrapper response, String objectId) {
        assertRuleResultSize(response, objectId, "MAIN", 1);
        assertFirstRuleExp(response, objectId, "MAIN", EMPTY_RULES_EXP_ID, "A", "A");
        assertFirstRuleExpConditionId(response, objectId, "MAIN", 1);
        assertFirstRuleExpActionType(response, objectId, "MAIN", "0");
        assertRuleExpIdsExactly(response, objectId, "ALL", EMPTY_RULES_EXP_ID);
    }

    private void assertNonEmptyRulesExperimentNotSelected(ValidatableResponseWrapper response, String objectId) {
        assertRuleExpIdsExactly(response, objectId, "MAIN", EMPTY_RULES_EXP_ID);
        assertRuleExpIdsExactly(response, objectId, "ALL", EMPTY_RULES_EXP_ID);
    }
}
