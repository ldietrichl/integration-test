package config.extensions.scheduler;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Supported corporate cases are discovered equally by Gradle and IDEA.
 * Real fixture/stand permissions are checked in preparation, never bypassed here.
 * Unsupported cases remain in the plan, not in the executable regression package.
 */
public final class SchedulerExecutionCondition extends SchedulerInfrastructureFailureExtension
        implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return ConditionEvaluationResult.enabled(
                "Supported regression: permissions and live preparation must still succeed");
    }
}
