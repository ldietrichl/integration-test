package steps.flow.scheduler;

import config.services.core.SchedulerSettings;
import config.services.core.StandFixtureUsers;
import config.services.core.StandSettings;
import flow.SchedulerInfrastructureFlow;
import infrastructure.kubernetes.KubernetesWorkloadControl;
import io.qameta.allure.Allure;
import java.util.*;
import java.util.concurrent.*;
import static io.qameta.allure.Allure.step;

/** JUnit-root-owned pause window; only confirmed pod/configuration state permits fixture writes. */
public final class SchedulerFixtureStandSteps {
    private static final Map<Object, Scope> SCOPES = new HashMap<>();
    private static final ThreadLocal<Object> RUN = new ThreadLocal<>();

    public static void bindRun(Object run) { RUN.set(Objects.requireNonNull(run)); }
    public static void unbindRun() { RUN.remove(); }

    private static Object runOwner() {
        Object owner = RUN.get();
        if (owner == null)
            throw new IllegalStateException("Managed fixtures require the inherited SchedulerRunScopeExtension");
        return owner;
    }
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final Set<String> EXPECTED_KEYS = Set.of(
            "SCHEDULER_SERVICE_TASK_JOB_ENABLED",
            "SCHEDULER_SERVICE_CLEAR_HUNG_TASKS_JOB_ENABLED", "SCHEDULER_SERVICE_CLEAR_OUTDATED_TASKS_JOB_ENABLED",
            "SCHEDULER_SERVICE_TASK_SCHEDULER_V2_ENABLED", "SCHEDULER_SERVICE_TASK_JOB_V2_ENABLED",
            "SCHEDULER_SERVICE_CLEAR_HUNG_TASKS_JOB_V2_ENABLED", "SCHEDULER_SERVICE_CLEAR_OUTDATED_TASKS_JOB_V2_ENABLED");
    private static final class Scope {
        final KubernetesWorkloadControl control;
        final Set<String> keys;
        final String environment = new StandSettings().environment;
        final infrastructure.scheduler.SchedulerRootReadinessProbe rootProbe;
        final long restoreTimeoutSeconds;
        boolean ready;
        boolean cleanupFailed;
        Scope(KubernetesWorkloadControl control, Set<String> keys,
              infrastructure.scheduler.SchedulerRootReadinessProbe rootProbe, long restoreTimeoutSeconds) {
            this.control = control; this.keys = keys; this.rootProbe = rootProbe;
            this.restoreTimeoutSeconds = restoreTimeoutSeconds;
        }
    }

    public static boolean readyForCurrentScenario() {
        Scope scope = CURRENT.get();
        return scope != null && scope.ready && !scope.cleanupFailed;
    }

    public static void requireCleanupReady() {
        Scope scope = CURRENT.get();
        if (scope == null || !readyForCurrentScenario())
            throw new IllegalStateException("FIXTURE_CLEANUP_PAUSE_UNCONFIRMED");
        scope.control.assertRegressionLease();
        scope.control.assertConfigMapFlagsDisabled(scope.keys);
    }

    public void prepare(Class<?> owner) {
        SchedulerSettings settings = new SchedulerSettings();
        if (!"true".equals(settings.optional("fixtures.enabled", "false"))
                || !"true".equals(settings.optional("fixtures.isolated", "false")))
            throw new IllegalStateException("Dedicated owned-fixture permissions are required before data preparation");
        var flow = new SchedulerInfrastructureFlow();
        // Resolve identities and DB compatibility BEFORE any stand mutation.
        if (StandFixtureUsers.current() == null)
            StandFixtureUsers.install(flow.dbCustomSteps().standUserFixtureSteps().selectTwoActiveUsers());
        flow.dbCustomSteps().schedulerBaselineSteps().requireSchema(settings);
        if (!settings.managedFixturesEnabled()) {
            settings.requireFixturePermission();
            return;
        }
        if (!config.services.container.KubernetesTunnelConfigScope.enabled())
            throw new IllegalStateException("Managed preparation requires stand Kubernetes management to be enabled");
        if (!settings.tunnelEnabled()) {
            if (!config.services.rest.RestMtlsConfiguration.enabled(settings.environment))
                throw new IllegalStateException("Managed preparation requires explicit stand mTLS or owned REST tunnels");
            config.services.rest.RestMtlsConfiguration.requireApprovedTarget(settings.baseUri());
            config.services.rest.RestMtlsConfiguration.apply(io.restassured.config.RestAssuredConfig.config());
        }
        synchronized (SCOPES) {
            Object run = runOwner();
            Scope scope = SCOPES.get(run);
            if (scope != null && !scope.environment.equals(settings.environment))
                throw new IllegalStateException("Do not change the selected stand inside an active scheduler run");
            if (scope != null && scope.cleanupFailed)
                throw new IllegalStateException("FIXTURE_CLEANUP_FAILED: no further fixtures; manual recovery required");
            if (scope == null) {
                Set<String> keys = new TreeSet<>(Arrays.asList(new StandSettings()
                        .required("workloads.scheduler.fixtures.job-keys").split(",")));
                if (!keys.equals(EXPECTED_KEYS))
                    throw new IllegalStateException("Explicit approval of all seven scheduler job switches is required");
                var workload = flow.infrastructureSteps().workloadSteps();
                // Capture TLS/application identity and budgets before creating the owned client or changing the stand.
                var rootProbe = new infrastructure.scheduler.SchedulerRootReadinessProbe(settings);
                long restoreBudget = Long.parseLong(new StandSettings().required("mutations.timeout.seconds"))
                        + rootProbe.timeoutSeconds() + 90L;
                Scope created = new Scope(workload.session("scheduler"), keys, rootProbe, restoreBudget);
                try {
                    timed("regression-run-lease", created.control::acquireRegressionLease);
                    created.control.rootReadinessProbe(created.rootProbe);
                    timed("fixture-configmap-pause", () -> created.control.pauseConfigMapFlags(keys));
                    created.control.replacementReason("fixture-baseline-apply-paused-config");
                    timed("fixture-pod-replacement", () -> workload.replacePod(created.control));
                    timed("fixture-ingress-ready", () -> new SchedulerWorkloadDiagnosticSteps().assertHealthy("fixture-stand-paused"));
                    timed("fixture-configmap-get", () -> created.control.assertConfigMapFlagsDisabled(keys));
                    created.ready = true;
                    SCOPES.put(run, created);
                    System.out.println("[scheduler-suite] Jobs paused for the selected JUnit run; restoration is deferred until root close");
                    scope = created;
                } catch (RuntimeException | Error failure) {
                    try { created.control.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                }
            }
            Scope active = scope;
            active.control.assertRegressionLease();
            step("Assert the selected stand still has paused scheduler jobs", () ->
                    active.control.assertConfigMapFlagsDisabled(active.keys));
            active.control.attachCurrentConfigMap();
            CURRENT.set(scope);
        }
    }

    /** One real job at a time; always return to the paused run baseline before fixture cleanup. */
    public static void withJobProfile(String jobKey, Runnable beforeEnable, Runnable observation) {
        Scope scope = CURRENT.get();
        if (scope == null || !readyForCurrentScenario())
            throw new IllegalStateException("A confirmed managed paused-fixture scope is required");
        if (!Set.of("SCHEDULER_SERVICE_TASK_JOB_V2_ENABLED",
                "SCHEDULER_SERVICE_CLEAR_HUNG_TASKS_JOB_V2_ENABLED",
                "SCHEDULER_SERVICE_CLEAR_OUTDATED_TASKS_JOB_V2_ENABLED").contains(jobKey))
            throw new IllegalArgumentException("Only explicitly implemented scheduler V2 jobs may run");
        config.services.core.SchedulerJobSettings.requireEnabled();
        scope.control.assertRegressionLease();
        scope.control.assertConfigMapFlagsDisabled(scope.keys);
        beforeEnable.run(); // Refuse foreign runnable tasks before the first profile mutation.
        Map<String, Boolean> desired = new TreeMap<>();
        scope.keys.forEach(key -> desired.put(key, false));
        desired.put("SCHEDULER_SERVICE_TASK_SCHEDULER_V2_ENABLED", true);
        desired.put(jobKey, true);
        var workload = new SchedulerInfrastructureFlow().infrastructureSteps().workloadSteps();
        var profile = workload.session("scheduler");
        scope.ready = false;
        Throwable primary = null;
        try {
            timed("real-job-configmap-profile", () -> profile.applyFixtureJobProfile(desired));
            profile.replacementReason("real-job-profile-apply");
            timed("real-job-pod-replacement", () -> workload.replacePod(profile));
            timed("real-job-ingress-ready", () -> new SchedulerWorkloadDiagnosticSteps().assertHealthy("real-job-profile"));
            observation.run();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                timed("real-job-profile-restore", profile::close);
                if (!profile.isRestored())
                    throw new IllegalStateException("Job profile rollback was not confirmed");
                scope.control.assertConfigMapFlagsDisabled(scope.keys);
                timed("real-job-restored-ingress", () -> new SchedulerWorkloadDiagnosticSteps().assertHealthy("real-job-paused-again"));
                scope.ready = true;
            } catch (RuntimeException | Error restoreFailure) {
                scope.cleanupFailed = true;
                scope.control.retainForManualRecovery(
                        "Job profile restoration failed; do not delete fixtures or resume original job switches");
                if (primary != null) primary.addSuppressed(restoreFailure);
                else throw restoreFailure;
            }
        }
    }

    public static void fixtureCleanupFailed() {
        Scope scope = CURRENT.get();
        if (scope != null) scope.cleanupFailed = true;
    }
    public static void finishScenario() {
        CURRENT.remove();
        StandFixtureUsers.clear();
        config.services.core.SchedulerApplicationIdentity.clear();
    }

    public static void restoreRun(Object owner) {
        Scope scope;
        synchronized (SCOPES) { scope = SCOPES.remove(owner); }
        if (scope == null) return;
        scope.ready = false;
        scope.control.disableAllureAttachments();
        if (scope.cleanupFailed) {
            scope.control.retainForManualRecovery("Fixture cleanup failed; resuming jobs might execute remaining fixtures");
            AssertionError recovery = new AssertionError("Jobs remain paused intentionally. Remove only owned fixtures, restore approved "
                    + "ConfigMap keys from private pre-image, then recreate scheduler pod. See recovery-status.txt.");
            // Manual-recovery mode forbids mutations, but must still release the run-owned client.
            try { scope.control.close(); }
            catch (RuntimeException | Error closeFailure) { recovery.addSuppressed(closeFailure); }
            throw recovery;
        }
        System.out.println("[scheduler-suite] Confirming original job switches; recreate only when an owned config rollback requires it");
        timed("root-config-and-pod-restore", () -> closeOnDedicatedThread(scope));
        if (!scope.control.isRestored())
            throw new AssertionError("Managed fixture stand restoration was not confirmed");
        // Root close has no active Allure test. Detailed recovery artifacts are saved by the control.
        System.out.println("[scheduler-suite] " + scope.control.restorationSummary()
                + "; httpVerifiedBeforeLeaseRelease=true");
    }

    private static void timed(String phase, Runnable action) {
        long started = System.nanoTime();
        boolean completed = false;
        System.out.println("[scheduler-phase] phase=" + phase + "; event=START");
        try { action.run(); completed = true; }
        finally {
            System.out.println("[scheduler-phase] phase=" + phase + "; event=END; completed=" + completed
                    + "; elapsedMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    private static void closeOnDedicatedThread(Scope scope) {
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "scheduler-fixture-root-restore");
            thread.setDaemon(true);
            return thread;
        };
        ExecutorService executor = Executors.newSingleThreadExecutor(factory);
        boolean interrupted = Thread.interrupted();
        Future<?> restore = executor.submit(() -> {
            Thread.interrupted();
            scope.control.close();
        });
        long timeoutSeconds = scope.restoreTimeoutSeconds;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException("Scheduler root restoration timed out");
                try {
                    restore.get(remaining, TimeUnit.NANOSECONDS);
                    return;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } catch (TimeoutException failure) {
            restore.cancel(true);
            scope.control.retainForManualRecovery("Root restoration timed out; automatic mutation replay is forbidden");
            throw new AssertionError("Scheduler fixture restoration exceeded " + timeoutSeconds
                    + " seconds; inspect private workload evidence before manual recovery", failure);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error error) throw error;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Scheduler fixture restoration failed", cause);
        } finally {
            executor.shutdownNow();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
