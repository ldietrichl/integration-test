package steps.flow.splitter.workedgroup;

import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;

import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class MapperWorkedGroupSteps extends WorkedGroupSteps {

    public void verifyDifferentConditionCase(ValidatableResponseWrapper response,
                                              SplitRequestDto request,
                                              long version,
                                              WorkedGroupCase testCase) {
        assertBasicResponseContract(response, request, version);
        assertSplittingResultsHaveUniqueObjectIds(response);

        if (testCase.expectedGroup() == null) {
            assertObjectWithoutMain(response, SINGLE_OBJECT_ID);
            return;
        }

        assertResultExp(response, SINGLE_OBJECT_ID, "MAIN", 269001L,
                testCase.expectedConditionId(), testCase.expectedGroup(), testCase.expectedGroup(),
                testCase.expectedActionType(), testCase.expectedResult());
        assertResultExp(response, SINGLE_OBJECT_ID, "ALL", 269001L,
                testCase.expectedConditionId(), testCase.expectedGroup(), testCase.expectedGroup(),
                testCase.expectedActionType(), testCase.expectedResult());
        assertMainHasNoExpFlags(response, SINGLE_OBJECT_ID);
        assertAllExpFlagsHaveAlternativeValue(response, SINGLE_OBJECT_ID, "false");
        assertFilteredFlag(response, SINGLE_OBJECT_ID, false);
    }

    public LoadConfigRequestDto configWithDifferentConditionPerGroup(long version) {
        ExperimentDto experiment = experiment(269001,
                SALT_2690,
                List.of(
                        objectParamEqualsCondition(1, "groupA", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "groupB", "1", "INTEGER"),
                        objectParamEqualsCondition(3, "groupC", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 2500), 1, "0", "101"),
                        groupWithDocResult("B", shares(2500, 5000), 2, "1", "202"),
                        groupWithDocResult("C", shares(5000, 7500), 3, "3", "303")));
        return singleExperimentConfig(EndpointMode.MAPPER, version, experiment);
    }

    public LoadConfigRequestDto configWithSameConditionForAllGroups(long version) {
        ExperimentDto experiment = experiment(269003,
                SALT_2690,
                List.of(objectParamEqualsCondition(1, "allGroups", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 2500), 1, "0", "101"),
                        groupWithDocResult("B", shares(2500, 5000), 1, "1", "202"),
                        groupWithDocResult("C", shares(5000, 7500), 1, "3", "303")));
        return singleExperimentConfig(EndpointMode.MAPPER, version, experiment);
    }

    public record WorkedGroupCase(String id,
                                   int rangeFrom,
                                   int rangeTo,
                                   String expectedGroup,
                                   int expectedConditionId,
                                   String expectedActionType,
                                   String expectedResult) {
        @Override
        public String toString() {
            return id;
        }
    }
}
