package flow;

import ru.sber.qa.flow.Flow;
import ru.sber.qa.flow.FlowRunner;

public class Flows extends RestFlows {
    protected static class FlowWithDb implements Flow, DbCustomFlow {
    }

    protected static class FlowWithDbRest implements Flow, DbCustomFlow, RestCustomFlow {
    }

    protected static FlowRunner<FlowWithDb> getFlowWithDb() {
        return FlowRunner.flowRunnerFor(FlowWithDb.class);
    }

    protected static FlowRunner<FlowWithDbRest> getFlowWithDbRest() {
        return FlowRunner.flowRunnerFor(FlowWithDbRest.class);
    }

}
