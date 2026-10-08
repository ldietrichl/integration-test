package steps.flow.splitter.workedgroup;

import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;

import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class MapperAlternativeSteps extends WorkedGroupSteps {

    public void verifyAlternativeResponse(ValidatableResponseWrapper response,
                                           SplitRequestDto request,
                                           long version,
                                           AlternativeCase testCase) {
        assertBasicResponseContract(response, request, version);
        assertSplittingResultsHaveUniqueObjectIds(response);

        assertResultExp(response,
                testCase.normalObjectId(),
                "MAIN",
                269004L,
                testCase.normalConditionId(),
                testCase.workedGroup(),
                testCase.workedGroup(),
                testCase.normalActionType(),
                testCase.normalResult());
        assertResultExp(response,
                testCase.normalObjectId(),
                "ALL",
                269004L,
                testCase.normalConditionId(),
                testCase.workedGroup(),
                testCase.workedGroup(),
                testCase.normalActionType(),
                testCase.normalResult());

        // Главное изменение EXPLAB-2690: в альтернативном MAIN expGroup/resultParams берутся
        // из группы, через которую эксперимент связан с текущим объектом, а finalExpGroup
        // отдельно сообщает реально сработавшую группу распределения.
        assertResultExp(response,
                testCase.alternativeObjectId(),
                "MAIN",
                269004L,
                testCase.alternativeConditionId(),
                testCase.linkedGroup(),
                testCase.workedGroup(),
                testCase.alternativeActionType(),
                testCase.alternativeResult());
        assertMainHasNoExpFlags(response, testCase.normalObjectId());
        assertMainHasNoExpFlags(response, testCase.alternativeObjectId());

        // Несработавшая связанная группа expGroup != finalExpGroup остаётся в полном КАП-логе,
        // но должна быть удалена из публичного ALL.
        assertRuleAbsent(response, testCase.alternativeObjectId(), "ALL");
        assertAllExpFlagsHaveAlternativeValue(response, testCase.normalObjectId(), "false");
    }

    public LoadConfigRequestDto alternativeConfig(long version) {
        ExperimentDto experiment = experiment(269004,
                SALT_2690,
                List.of(
                        objectParamEqualsCondition(1, "left", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "right", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 5000), 1, "1", "101"),
                        groupWithDocResult("B", shares(5000, 10000), 2, "3", "202")));
        return singleExperimentConfig(EndpointMode.MAPPER, version, experiment);
    }

    public record AlternativeCase(String id,
                                   int rangeFrom,
                                   int rangeTo,
                                   String workedGroup,
                                   String normalObjectId,
                                   int normalConditionId,
                                   String normalActionType,
                                   String normalResult,
                                   String alternativeObjectId,
                                   int alternativeConditionId,
                                   String linkedGroup,
                                   String alternativeActionType,
                                   String alternativeResult) {
        @Override
        public String toString() {
            return id;
        }
    }
}
