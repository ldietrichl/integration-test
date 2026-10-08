package config.extensions.scheduler;

import config.services.container.KubernetesTunnelConfigScope;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Diagnostics remain independent: one failed probe must not abort later probes. */
public final class SchedulerInfrastructureCondition implements ExecutionCondition {
    @Override public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return KubernetesTunnelConfigScope.enabled()
                ? ConditionEvaluationResult.enabled("Stand infrastructure enabled")
                : ConditionEvaluationResult.disabled("Enable stand.<env>.kubernetes.enabled in stand.properties");
    }
}