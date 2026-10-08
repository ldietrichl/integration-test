package steps.flow.splitter.workedgroup;

import dto.splitter.config.ExperimentDto;

import java.util.List;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class MapperMixedObjectsSteps extends WorkedGroupSteps {

    public static final String NO_MAIN_OBJECT_ID = "26900000-0000-0000-0000-000000000005";
    public static final String UNLINKED_OBJECT_ID = "26900000-0000-0000-0000-000000000006";

    public ExperimentDto alternativeExperiment() {
        return experiment(269011,
                SALT_2690,
                List.of(
                        objectParamEqualsCondition(1, "left", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "right", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 5000), 1, "1", "101"),
                        groupWithDocResult("B", shares(5000, 10000), 2, "3", "202")));
    }

    public ExperimentDto noMainExperiment() {
        return experiment(269012,
                SALT_2690 + "-NO-MAIN",
                List.of(objectParamEqualsCondition(1, "noMain", "1", "INTEGER")),
                List.of(group("A", shares(0, 10000), List.of(
                        resultWithParams(1, param("result", "999", "INTEGER"))))));
    }
}
