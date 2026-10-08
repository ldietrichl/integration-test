package flow;

import ru.sber.qa.containers.flow.ContainerServiceFlow;
import ru.sber.qa.flow.Flow;
import steps.container.ContainerPackageDiagnosticSteps;

public final class ContainerPackageDiagnosticFlow implements Flow, ContainerServiceFlow {
    public ContainerPackageDiagnosticSteps diagnosticSteps() {
        return new ContainerPackageDiagnosticSteps(this);
    }
}
