package steps.flow.splitter.workedgroup;

import dto.splitter.config.ExperimentDto;

import java.util.List;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class ReactionsFinalExperimentsSteps extends WorkedGroupSteps {

    @Override
    protected EndpointMode endpointMode() { return EndpointMode.REACTIONS; }

    public ExperimentDto reactionExperiment(int expId, int layerId, int layerPriority, String resultValue) {
        return layeredExperiment(expId,
                SALT_2690 + "-" + expId,
                layerId,
                layerPriority,
                List.of(objectParamEqualsCondition(1, "segment", "2690", "INTEGER")),
                List.of(groupWithDocResult("A", shares(0, 10000), 1, "1", resultValue)));
    }
}
