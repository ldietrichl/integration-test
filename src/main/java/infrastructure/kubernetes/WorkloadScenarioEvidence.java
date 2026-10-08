package infrastructure.kubernetes;

import io.qameta.allure.Allure;
import java.time.Instant;
import java.util.*;

/** Test journal only; pod/configuration evidence belongs to the common Fabric8 session. */
public final class WorkloadScenarioEvidence {
    private final Instant started = Instant.now();
    private final List<String> timeline = new ArrayList<>();
    private KubernetesWorkloadControl session;
    private String status = "NOT_STARTED";
    private boolean failed;
    private List<ConfigMapState> configMaps = List.of();
    public void configMaps(List<ConfigMapState> states) {
        configMaps = List.copyOf(states);
        captureConfigMaps("до подготовки");
    }
    public void captureConfigMaps(String phase) { if (session != null) session.captureScenarioConfigMaps(phase, configMaps); }
    public void operationStarted() {
        observe(() -> { if (session != null) session.startScenarioOperation(Instant.now()); });
        event("OPERATION_START");
    }
    public void operationFinished() {
        observe(() -> { if (session != null) session.finishScenarioOperation(Instant.now()); });
        event("OPERATION_FINISH");
    }
    private void observe(Runnable action) {
        try { action.run(); }
        catch (RuntimeException | AssertionError failure) { event("EVIDENCE_UNAVAILABLE " + failure.getClass().getSimpleName()); }
    }
    private static final ThreadLocal<WorkloadScenarioEvidence> CURRENT = new ThreadLocal<>();
    private final String label;
    private boolean finished;
    public WorkloadScenarioEvidence(String label, String title) {
        this.label = Objects.requireNonNull(label);
        event("SCENARIO_START " + title);
    }
    public static void open(String label, String title) {
        if (CURRENT.get() != null) throw new IllegalStateException("Previous workload evidence scope was not closed");
        CURRENT.set(new WorkloadScenarioEvidence(label, title));
    }
    public static WorkloadScenarioEvidence current() {
        return Objects.requireNonNull(CURRENT.get(), "@WorkloadScenario is required");
    }
    public static WorkloadScenarioEvidence currentOrNull() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
    public void bind(KubernetesWorkloadControl session) {
        this.session = session;
        status = "PREPARING";
        session.beginScenarioEvidence(started);
    }
    public void prepared() { status = "MANAGED_STATE_READY"; captureConfigMaps("перед операцией"); event(status); }
    public synchronized void event(String text) {
        if (timeline.size() < 2000) timeline.add(Instant.now() + " " + text);
        else if (timeline.size() == 2000) timeline.add("[timeline truncated at 2000 events]");
    }
    public void failed(Throwable failure) {
        failed = true;
        if ("PREPARING".equals(status)) status = "PREPARATION_FAILED";
        event("SCENARIO_THROW " + failure.getClass().getName());
        Arrays.stream(failure.getStackTrace()).limit(20).forEach(frame -> event("at " + frame));
    }
    public void finish() {
        if (finished) return;
        finished = true;
        try { captureConfigMaps("после операции"); }
        catch (RuntimeException | AssertionError failure) { event("CONFIGMAP_EVIDENCE_UNAVAILABLE " + failure.getClass().getSimpleName()); }
        if (session != null) {
            try { session.finishScenarioEvidence(label + " after scenario", failed); }
            catch (RuntimeException | AssertionError failure) { event("COMMON_EVIDENCE_UNAVAILABLE " + failure.getClass().getSimpleName()); }
        }
        event("SCENARIO_FINISH " + status);
        attach("scenario-log", "text/plain", String.join("\n", timeline), ".txt");
    }
    private void attach(String name, String type, String text, String extension) {
        try { Allure.addAttachment(label + " " + name, type, text, extension); }
        catch (RuntimeException ignored) { /* preserve the original test failure */ }
    }
}
