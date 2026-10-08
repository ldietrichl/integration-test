package config.extensions;

import infrastructure.kubernetes.WorkloadScenarioEvidence;
import org.junit.jupiter.api.extension.*;

/** One evidence window per test invocation; setup/body/teardown are kept distinct. */
public final class WorkloadScenarioEvidenceExtension implements BeforeEachCallback, BeforeTestExecutionCallback,
        AfterTestExecutionCallback, AfterEachCallback {
    @Override public void beforeEach(ExtensionContext context) {
        WorkloadScenario annotation = context.getRequiredTestClass().getAnnotation(WorkloadScenario.class);
        WorkloadScenarioEvidence.open(annotation == null ? context.getRequiredTestClass().getSimpleName() : annotation.value(),
                context.getDisplayName());
    }
    @Override public void beforeTestExecution(ExtensionContext context) {
        WorkloadScenarioEvidence.current().operationStarted();
    }
    @Override public void afterTestExecution(ExtensionContext context) {
        var evidence = WorkloadScenarioEvidence.current();
        evidence.operationFinished();
        context.getExecutionException().ifPresentOrElse(evidence::failed, () -> evidence.event("TEST_METHOD_PASSED"));
    }
    @Override public void afterEach(ExtensionContext context) {
        var evidence = WorkloadScenarioEvidence.currentOrNull();
        try {
            if (evidence != null) {
                context.getExecutionException().ifPresent(evidence::failed);
                // Normally attached by an @AfterEach fixture; fallback covers failed setup.
                evidence.finish();
            }
        } finally { WorkloadScenarioEvidence.clear(); }
    }
}
