package config.extensions.scheduler;

import config.services.container.KubernetesTunnelConfigScope;
import config.services.core.SchedulerSettings;
import config.services.core.StandSettings;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.TestAbortedException;

import java.util.HashMap;
import java.util.Map;

/**
 * Run-scoped dependency circuit breaker. The first failure is rethrown unchanged;
 * later dependent scenarios abort before fixture preparation. Never gates cleanup.
 */
public abstract class SchedulerInfrastructureFailureExtension implements BeforeEachCallback,
        LifecycleMethodExecutionExceptionHandler, TestExecutionExceptionHandler {
    private static final ExtensionContext.Namespace STORE =
            ExtensionContext.Namespace.create(SchedulerInfrastructureFailureExtension.class);
    private record Scope(String environment, String api, String context, String namespace, String kubeconfig) {}
    private record Blocker(String code, String test) {}
    private static final class RunState {
        Blocker authentication;
        final Map<String, Blocker> fixturePreparation = new HashMap<>();
    }

    private static RunState state(ExtensionContext context) {
        StandSettings stand = new StandSettings();
        Scope scope = new Scope(stand.environment, setting(stand, "api-server"), setting(stand, "context"),
                setting(stand, "namespace"), setting(stand, "kubeconfig"));
        return context.getRoot().getStore(STORE).getOrComputeIfAbsent(scope, key -> new RunState(), RunState.class);
    }
    private static String setting(StandSettings stand, String key) {
        return stand.optional("kubernetes." + key, "kubernetes." + stand.environment + "." + key,
                "kubernetes." + key, "");
    }
    private static boolean fixtureClass(ExtensionContext context) {
        String type = context.getRequiredTestClass().getSimpleName();
        return type.equals("SchedulerApiRegressionFlowTest")
                || type.equals("SchedulerManagedRegressionFlowTest")
                || type.equals("SchedulerRealStandRegressionFlowTest")
                || type.equals("SchedulerWorkloadRegressionFlowTest");
    }
    private static boolean openShiftDependent(ExtensionContext context) {
        String type = context.getRequiredTestClass().getName();
        if (type.endsWith(".SchedulerPreflightFlowTest")) return false;
        if (type.contains(".scheduler.infrastructure.") || type.endsWith(".SchedulerWorkloadRegressionFlowTest"))
            return KubernetesTunnelConfigScope.enabled();
        SchedulerSettings settings = new SchedulerSettings();
        return settings.tunnelEnabled() || (fixtureClass(context) && settings.managedFixturesEnabled()
                && KubernetesTunnelConfigScope.enabled());
    }
    private static String identity(ExtensionContext context) {
        return context.getRequiredTestClass().getName() + "#"
                + context.getTestMethod().map(java.lang.reflect.Method::getName).orElse("<lifecycle>");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        RunState state = state(context);
        Blocker blocked;
        synchronized (state) {
            blocked = openShiftDependent(context) ? state.authentication : null;
            if (blocked == null && fixtureClass(context)
                    && !context.getTestMethod().map(m -> m.getName().equals("sch135")).orElse(false))
                blocked = state.fixturePreparation.get(context.getRequiredTestClass().getName());
        }
        if (blocked != null)
            throw new TestAbortedException("DEPENDENCY_BLOCKED: " + blocked.code()
                    + "; first failure: " + blocked.test()
                    + ". Scenario body was not run. Resolve the original failure and start a new test run. "
                    + "Cleanup and DB-only preflight are not disabled.");
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        remember(context, failure, true);
        throw failure;
    }
    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        remember(context, failure, false);
        throw failure;
    }

    private static void remember(ExtensionContext context, Throwable failure, boolean preparation) {
        if (failure instanceof TestAbortedException) return;
        boolean authentication = authenticationFailure(failure) && openShiftDependent(context);
        boolean fixtures = preparation && fixtureClass(context) && fixturePreparationFailure(failure);
        if (!authentication && !fixtures) return; // Business assertions are never used to skip other tests.
        RunState state = state(context);
        synchronized (state) {
            if (authentication && state.authentication == null)
                state.authentication = new Blocker("OPENSHIFT_AUTH_REQUIRED", identity(context));
            if (fixtures)
                state.fixturePreparation.putIfAbsent(context.getRequiredTestClass().getName(),
                        new Blocker("FIXTURE_PREPARATION_FAILED", identity(context)));
        }
    }
    private static boolean authenticationFailure(Throwable failure) {
        for (int depth = 0; failure != null && depth < 12; depth++, failure = failure.getCause()) {
            String message = failure.getMessage();
            if (message != null && (message.startsWith("WORKLOAD_READ_AUTH_REQUIRED")
                    || message.equals("OC_AUTH_REQUIRED") || message.equals("OC_READ_AUTH_REQUIRED")
                    || (message.startsWith("Kubernetes ") && message.contains("[causeCode=AUTH_REQUIRED]"))))
                return true;
        }
        return false;
    }
    private static boolean fixturePreparationFailure(Throwable failure) {
        for (int depth = 0; failure != null && depth < 12; depth++, failure = failure.getCause())
            for (StackTraceElement frame : failure.getStackTrace())
                if (frame.getClassName().equals("steps.flow.scheduler.SchedulerFixtureStandSteps")
                        && frame.getMethodName().equals("prepare")) return true;
        return false;
    }
}
