package config.extensions.infrastructure;

import config.services.container.ContainerDiagnosticsSettings;
import config.services.container.DiagnosticContainerServiceConfiguration;
import infrastructure.kubernetes.ContainerDiagnosticEvidence;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** No failure-based cascading skips: Java and oc probes remain independent. */
public final class ContainerPackageDiagnosticExtension
        implements ExecutionCondition, AfterEachCallback, AfterAllCallback {
    @Override public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return ContainerDiagnosticsSettings.local().flag("enabled", false)
                ? ConditionEvaluationResult.enabled("Explicit stand diagnostic profile enabled")
                : ConditionEvaluationResult.disabled("Set stand.<env>.container-diagnostics.enabled=true");
    }
    @Override public void afterEach(ExtensionContext context) {
        DiagnosticContainerServiceConfiguration.closeOwnedClients();
    }
    @Override public void afterAll(ExtensionContext context) {
        try { DiagnosticContainerServiceConfiguration.closeOwnedClients(); }
        finally { ContainerDiagnosticEvidence.finish(); }
    }
}
