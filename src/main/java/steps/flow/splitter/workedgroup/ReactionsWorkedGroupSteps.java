package steps.flow.splitter.workedgroup;

import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;

import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class ReactionsWorkedGroupSteps extends WorkedGroupSteps {

    @Override protected EndpointMode endpointMode() { return EndpointMode.REACTIONS; }

    public void verifyReactionsResponse(ValidatableResponseWrapper response,
                                         SplitRequestDto request,
                                         long version,
                                         ReactionsCase testCase) {
        org.junit.jupiter.api.Assertions.assertAll("REACTIONS objects",
                () -> assertBasicResponseContract(response, request, version),
                () -> {
        assertResultExp(response,
                testCase.matchedObjectId(),
                "MAIN",
                269006L,
                testCase.matchedConditionId(),
                testCase.workedGroup(),
                testCase.workedGroup(),
                testCase.expectedActionType(),
                testCase.expectedResult());
        assertEveryRuleExpUsesWorkedGroup(response, testCase.matchedObjectId(), "MAIN");
        assertMainHasNoExpFlags(response, testCase.matchedObjectId());
        assertResultExp(response, testCase.matchedObjectId(), "ALL", 269006L,
                testCase.matchedConditionId(), testCase.workedGroup(), testCase.workedGroup(),
                testCase.expectedActionType(), testCase.expectedResult());
            assertEveryRuleExpUsesWorkedGroup(response, testCase.matchedObjectId(), "ALL");
            assertAllExpFlagsHaveAlternativeValue(response, testCase.matchedObjectId(), "false");
                },
                () -> assertObjectWithoutMain(response, testCase.linkedObjectId()),
                () -> assertNoAlternativeTrueAnywhere(response));
    }

    public LoadConfigRequestDto reactionsAlternativeTopologyConfig(long version) {
        ExperimentDto experiment = experiment(269006,
                SALT_2690,
                List.of(
                        objectParamEqualsCondition(1, "left", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "right", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 5000), 1, "1", "101"),
                        groupWithDocResult("B", shares(5000, 10000), 2, "3", "202")));
        return singleExperimentConfig(EndpointMode.REACTIONS, version, experiment);
    }

    public record ReactionsCase(String id,
                                 int rangeFrom,
                                 int rangeTo,
                                 String workedGroup,
                                 String matchedObjectId,
                                 int matchedConditionId,
                                 String expectedActionType,
                                 String expectedResult,
                                 String linkedObjectId,
                                 int linkedConditionId,
                                 String linkedGroup,
                                 String linkedActionType,
                                 String linkedResult) {
        @Override
        public String toString() {
            return id;
        }
    }
}
