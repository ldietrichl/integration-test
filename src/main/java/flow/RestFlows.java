package flow;

import ru.sber.qa.flow.Flow;
import ru.sber.qa.flow.FlowRunner;

/** Base for REST-only suites; does not require database flows or configuration. */
public abstract class RestFlows {
    protected static class FlowWithRest implements Flow, RestCustomFlow {
    }

    protected static FlowRunner<FlowWithRest> getFlowWithRest() {
        return FlowRunner.flowRunnerFor(FlowWithRest.class);
    }
}
