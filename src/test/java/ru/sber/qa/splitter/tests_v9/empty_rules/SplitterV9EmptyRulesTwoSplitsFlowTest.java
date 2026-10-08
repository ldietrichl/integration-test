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
@DisplayName("Tests-v9. MAPPER: configCommId in [] + configCommId in [1]")
@AnyConfigLoadMode
public class SplitterV9EmptyRulesTwoSplitsFlowTest extends AbstractSplitterV9FlowTest {

    private static final long EMPTY_VALUES_EXP_ID = 1L;
    private static final long MATCHING_EXP_ID = 2L;
    private static final String SALT = "2406GsjJBv";
    private static final String NOT_FOUND_OBJECT_ID = "v9-empty-rules-not-found";
    private static final String FOUND_OBJECT_ID = "v9-empty-rules-found";

    @Test
    @DisplayName("SPL-V9-EMPTY-RULES-01. Два эксперимента: configCommId in [] и configCommId in [1], два split-запроса")
    void mapperConfigWithEmptyValuesRuleAndMatchingRuleShouldReturnEmptyThenFoundResult() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                configCommIdInEmptyValuesExperiment(),
                configCommIdInOneExperiment());

        SplitRequestDto notFoundRequest = splitRequest(
                "SPL-V9-EMPTY-RULES-01-NOT-FOUND",
                objectWithUniqueId("empty-rules-not-found-uid",
                        NOT_FOUND_OBJECT_ID,
                        param("configCommId", "2", "INTEGER")));

        SplitRequestDto foundRequest = splitRequest(
                "SPL-V9-EMPTY-RULES-01-FOUND",
                objectWithUniqueId("empty-rules-found-uid",
                        FOUND_OBJECT_ID,
                        param("configCommId", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config с двумя экспериментами из примера: expId=1 values=[], expId=2 values=[1]",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split #1: configCommId=2, совпадений быть не должно",
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, notFoundRequest);
                            assertBasicResponseContract(response, notFoundRequest, version);
                            assertSplittingResultsHaveUniqueObjectIds(response);
                            assertObjectEmptyOrAbsent(response, NOT_FOUND_OBJECT_ID);
                            assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                        })
                .step("Выполняем split #2: configCommId=1, должен сработать эксперимент expId=2",
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, foundRequest);
                            assertBasicResponseContract(response, foundRequest, version);
                            assertSplittingResultsHaveUniqueObjectIds(response);
                            assertMatchingExperimentSelected(response);
                            assertEmptyValuesExperimentNotSelected(response);
                            assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                        })
                .run();
    }

    /**
     * Эксперимент из первого примера на фото:
     * id=1, conditionId=1, rules=[[configCommId in []]], group=A, share=0..10000, actionType=0.
     */
    private ExperimentDto configCommIdInEmptyValuesExperiment() {
        return experimentFromPhoto((int) EMPTY_VALUES_EXP_ID, List.<String>of());
    }

    /**
     * Эксперимент из второго примера на фото:
     * id=2, conditionId=1, rules=[[configCommId in ["1"]]], group=A, share=0..10000, actionType=0.
     */
    private ExperimentDto configCommIdInOneExperiment() {
        return experimentFromPhoto((int) MATCHING_EXP_ID, List.of("1"));
    }

    private ExperimentDto experimentFromPhoto(int id, List<String> values) {
        return ExperimentDto.builder()
                .id(id)
                .layerId(null)
                .layerPriority(null)
                .salt(SALT)
                .objectSelectConditions(List.of(configCommIdInCondition(values)))
                .groups(List.of(fullRangeActionTypeZeroGroup()))
                .build();
    }

    private ObjectSelectConditionDto configCommIdInCondition(List<String> values) {
        RuleDto rule = RuleDto.builder()
                .dataType("INTEGER")
                .paramCode("configCommId")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("in")
                .values(values)
                .build();

        return condition(1, List.of(List.of(rule)));
    }

    private GroupDto fullRangeActionTypeZeroGroup() {
        return group("A",
                List.of(share(0, 10000)),
                List.of(resultWithParams(1, param("actionType", "0", "INTEGER"))));
    }

    private void assertMatchingExperimentSelected(ValidatableResponseWrapper response) {
        assertRuleResultSize(response, FOUND_OBJECT_ID, "MAIN", 1);
        assertFirstRuleExp(response, FOUND_OBJECT_ID, "MAIN", MATCHING_EXP_ID, "A", "A");
        assertFirstRuleExpConditionId(response, FOUND_OBJECT_ID, "MAIN", 1);
        assertFirstRuleExpActionType(response, FOUND_OBJECT_ID, "MAIN", "0");
        assertRuleExpIdsExactly(response, FOUND_OBJECT_ID, "MAIN", MATCHING_EXP_ID);
        assertRuleExpIdsExactly(response, FOUND_OBJECT_ID, "ALL", MATCHING_EXP_ID);
    }

    private void assertEmptyValuesExperimentNotSelected(ValidatableResponseWrapper response) {
        assertRuleExpIdsExactly(response, FOUND_OBJECT_ID, "MAIN", MATCHING_EXP_ID);
        assertRuleExpIdsExactly(response, FOUND_OBJECT_ID, "ALL", MATCHING_EXP_ID);
    }
}
