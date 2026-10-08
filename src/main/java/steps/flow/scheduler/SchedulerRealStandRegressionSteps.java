package steps.flow.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import config.services.core.StandSettings;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.function.Executable;
import request.scheduler.SchedulerTestDataFactory;
import steps.db.scheduler.SchedulerDbSteps;
import util.scheduler.SchedulerAssertions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static request.scheduler.SchedulerTestDataFactory.firstAction;
import static steps.rest.scheduler.SchedulerSteps.expect;
import static util.scheduler.SchedulerAssertions.*;

/** Real dependencies only. Every row is owned; only scheduler job switches and its pod may change. */
public final class SchedulerRealStandRegressionSteps extends SchedulerScenarioSupport {
    private static final String EXECUTE = "SCHEDULER_SERVICE_TASK_JOB_V2_ENABLED";
    private static final String HUNG = "SCHEDULER_SERVICE_CLEAR_HUNG_TASKS_JOB_V2_ENABLED";
    private static final String OUTDATED = "SCHEDULER_SERVICE_CLEAR_OUTDATED_TASKS_JOB_V2_ENABLED";
    private static final long DAY = 86_400_000L;

    public void registryVersionIsolation() {
        var data = new SchedulerTestDataFactory(settings());
        getFlowWithDbRest()
                .step("Register owned data for version isolation", flow -> prepareFixture(flow, data, 95))
                .step("Compare version 1 with non-null versions 0 and 2 under identical filters", flow -> {
                    var db = flow.dbCustomSteps().schedulerSteps();
                    long wanted = seedTask(flow, data, 1, "PLANNED");
                    seedTask(flow, data, 0, "PLANNED");
                    seedTask(flow, data, 2, "PLANNED");
                    var before = db.tasks(data.owner);
                    var rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200));
                    assertEquals(1, rows.size(), "Only one version-1 row is eligible");
                    assertEquals(Set.of(wanted), ids(rows));
                    assertEquals(before, db.tasks(data.owner), "Registry must not mutate any version");
                }).run();
    }

    public void deleteCascade() {
        var data = new SchedulerTestDataFactory(settings());
        getFlowWithDbRest()
                .step("Register owned rows for cascade checks", flow -> prepareFixture(flow, data, 58))
                .step("Delete planned rows and assert both tables plus an unaffected control", flow -> {
                    var db = flow.dbCustomSteps().schedulerSteps();
                    long first = seedTask(flow, data, 1, "PLANNED");
                    long second = seedTask(flow, data, 1, "PLANNED");
                    long control = seedTask(flow, data, 1, "COMPLETED");
                    Map<String, Object> before = db.task(control);
                    assertEquals(3, db.tasks(data.owner).size());
                    expect(flow.restCustomSteps().schedulerSteps().deletePlanned("EXP", data.objectId), 200);
                    assertAll("Task/action cascade and retained completed row",
                            () -> assertTrue(db.query("SELECT id FROM scheduler.task WHERE id IN (?,?)", first, second).isEmpty()),
                            () -> assertTrue(db.query("SELECT id FROM scheduler.task_action WHERE task_id IN (?,?)", first, second).isEmpty()),
                            () -> assertEquals(before, db.task(control)),
                            () -> assertEquals(1, db.tasks(data.owner).size()));
                }).run();
    }

    public void explicitNullValidation() {
        var data = new SchedulerTestDataFactory(settings());
        getFlowWithDbRest()
                .step("Register owned negative requests", flow -> prepareFixture(flow, data, 36))
                .step("Reject explicit nulls without changing owned data", flow -> {
                    var db = flow.dbCustomSteps().schedulerSteps();
                    seedTask(flow, data, 1, "COMPLETED");
                    List<Executable> checks = new ArrayList<>();
                    for (String field : List.of("splittingPointCode", "scheduleDateTime", "createdBy", "actions")) {
                        checks.add(() -> Allure.step("Explicit null: " + field, () -> {
                            var request = data.createTaskV2();
                            request.put(field, null);
                            var before = db.tasks(data.owner);
                            assertAll("HTTP 400 and no persistence for null " + field,
                                    () -> expect(flow.restCustomSteps().schedulerSteps().createV2(request), 400),
                                    () -> assertEquals(before, db.tasks(data.owner)));
                        }));
                    }
                    for (String field : List.of("objectType", "objectId", "objectName", "action")) {
                        checks.add(() -> Allure.step("Explicit nested null: " + field, () -> {
                            var request = data.createTaskV2();
                            firstAction(request).put(field, null);
                            var before = db.tasks(data.owner);
                            assertAll("HTTP 400 and no persistence for null action field " + field,
                                    () -> expect(flow.restCustomSteps().schedulerSteps().createV2(request), 400),
                                    () -> assertEquals(before, db.tasks(data.owner)));
                        }));
                    }
                    assertAll("All documented required fields, including explicit nulls", checks);
                }).run();
    }

    public void invalidSecondAction() {
        var data = new SchedulerTestDataFactory(settings());
        getFlowWithDbRest()
                .step("Register a two-action negative creation request", flow -> prepareFixture(flow, data, 47))
                .step("Reject a bad second action without persisting the first", flow -> {
                    var db = flow.dbCustomSteps().schedulerSteps();
                    seedTask(flow, data, 1, "COMPLETED");
                    var before = db.tasks(data.owner);
                    var request = data.createTaskV2();
                    var second = data.action("EXP", data.objectId + 1, "STOP");
                    second.remove("objectId");
                    request.put("actions", List.of(new LinkedHashMap<>(firstAction(request)), second));
                    assertAll("Validation atomicity; not a database-fault rollback test",
                            () -> expect(flow.restCustomSteps().schedulerSteps().createV2(request), 400),
                            () -> assertEquals(before, db.tasks(data.owner)));
                }).run();
    }

    public void unsupportedCj() { unsupported("CJ", "START"); }
    public void unsupportedSplit() { unsupported("SPLIT", "START"); }
    public void unsupportedPilot() { unsupported("PILOT", "START"); }
    public void unsupportedAction() { unsupported("EXP", "REPEAT_EXP"); }
    public void cleanupHung() { cleanup(true); }
    public void cleanupOutdated() { cleanup(false); }

    private void unsupported(String type, String action) {
        var data = new SchedulerTestDataFactory(settings());
        getFlowWithDbRest()
                .step("Prepare owned unsupported-action witnesses with all jobs paused", flow -> prepareFixture(flow, data, 72))
                .step("Run the real V2 task job; keep other statuses, versions and future rows unchanged", flow -> {
                    var db = flow.dbCustomSteps().schedulerSteps();
                    long now = db.clockMillis();
                    long eligible = seedTask(flow, data, 1, "PLANNED", data.objectId, type, action,
                            settings().user(), now - 60_000L);
                    List<Long> controls = new ArrayList<>();
                    for (String status : List.of("IN_PROGRESS", "COMPLETED", "ERROR", "NOT_STARTED"))
                        controls.add(seedTask(flow, data, 1, status, data.objectId, type, action,
                                settings().user(), now - 60_000L));
                    controls.add(seedTask(flow, data, 0, "PLANNED", data.objectId, type, action,
                            settings().user(), now - 60_000L));
                    controls.add(seedTask(flow, data, 2, "PLANNED", data.objectId, type, action,
                            settings().user(), now - 60_000L));
                    controls.add(seedTask(flow, data, 1, "PLANNED", data.objectId, type, action,
                            settings().user(), now + 7 * DAY));
                    Map<Long, Map<String, Object>> snapshots = snapshots(db, controls);
                    long before = db.clockMillis();
                    SchedulerFixtureStandSteps.withJobProfile(EXECUTE,
                            () -> db.requireNoForeignRunnableTasks(data.owner),
                            () -> db.awaitStatus(eligible, "ERROR", timeout()));
                    long after = db.clockMillis();
                    var actual = db.task(eligible);
                    assertAll("Unsupported task result and non-target isolation",
                            () -> assertEquals("ERROR", actual.get("status")),
                            () -> assertFinished(actual, before, after, true),
                            () -> assertWithin(actual.get("start_datetime"), before, after, "start_datetime"),
                            () -> assertEquals(1, number(actual.get("version"))),
                            () -> assertControls(db, snapshots));
                    attach("Unsupported " + type + "/" + action, db, data);
                }).run();
    }

    private void cleanup(boolean hung) {
        var data = new SchedulerTestDataFactory(settings());
        getFlowWithDbRest()
                .step("Prepare owned cleanup witnesses while scheduler jobs are paused", flow -> prepareFixture(flow, data, hung ? 86 : 89))
                .step("Observe the selected real cleanup job and preserve other statuses/versions", flow -> {
                    var db = flow.dbCustomSteps().schedulerSteps();
                    long now = db.clockMillis();
                    long age = cleanupAgeMillis(hung);
                    String selected = hung ? "IN_PROGRESS" : "PLANNED";
                    long target = seedTask(flow, data, 1, selected, data.objectId, "CJ", "START",
                            settings().user(), now - age);
                    if (hung) db.setOwnedExecutionTimes(data.owner, target, now - age, null);
                    List<Long> controls = new ArrayList<>();
                    for (String status : List.of("PLANNED", "IN_PROGRESS", "COMPLETED", "ERROR", "NOT_STARTED")) {
                        if (status.equals(selected)) continue;
                        long id = seedTask(flow, data, 1, status, data.objectId, "CJ", "START",
                                settings().user(), now - age);
                        if (hung) db.setOwnedExecutionTimes(data.owner, id, now - age, null);
                        controls.add(id);
                    }
                    for (int version : new int[]{0, 2}) {
                        long id = seedTask(flow, data, version, selected, data.objectId, "CJ", "START",
                                settings().user(), now - age);
                        if (hung) db.setOwnedExecutionTimes(data.owner, id, now - age, null);
                        controls.add(id);
                    }
                    long future = seedTask(flow, data, 1, selected, data.objectId, "CJ", "START",
                            settings().user(), now + 7 * DAY);
                    if (hung) db.setOwnedExecutionTimes(data.owner, future, now + 7 * DAY, null);
                    controls.add(future);
                    Map<Long, Map<String, Object>> snapshots = snapshots(db, controls);
                    long before = db.clockMillis();
                    String expected = hung ? "ERROR" : "NOT_STARTED";
                    SchedulerFixtureStandSteps.withJobProfile(hung ? HUNG : OUTDATED,
                            () -> db.requireNoForeignRunnableTasks(data.owner),
                            () -> db.awaitStatus(target, expected, timeout()));
                    long after = db.clockMillis();
                    var actual = db.task(target);
                    assertAll("Cleanup transition, audit and non-target isolation",
                            () -> assertEquals(expected, actual.get("status")),
                            () -> assertFinished(actual, before, after, hung),
                            () -> assertEquals(now - age, number(actual.get("schedule_datetime"))),
                            () -> assertControls(db, snapshots));
                    attach(hung ? "Hung task cleanup" : "Outdated task cleanup", db, data);
                    Allure.addAttachment("Coverage boundary",
                            "Real database clock and far-side witnesses; exact T/T+/-1ms boundaries are not claimed.");
                }).run();
    }

    private static Map<Long, Map<String, Object>> snapshots(SchedulerDbSteps db, List<Long> ids) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, db.task(id)));
        return result;
    }
    private static void assertControls(SchedulerDbSteps db, Map<Long, Map<String, Object>> before) {
        assertAll("Every non-target witness must remain byte-for-value equivalent in DB",
                before.entrySet().stream().map(entry -> (Executable) () ->
                        assertEquals(entry.getValue(), db.task(entry.getKey()), "Unexpected mutation: " + entry.getKey())));
    }
    private static void assertFinished(Map<String, Object> row, long before, long after, boolean hasEnd) {
        assertAll("Documented terminal audit fields",
                () -> assertEquals(-1, number(row.get("updated_by"))),
                () -> assertTrue(row.get("result_description") instanceof String
                        && !row.get("result_description").toString().isBlank(), "Nonblank cause required"),
                () -> {
                    if (hasEnd) assertWithin(row.get("end_datetime"), before, after, "end_datetime");
                    else assertNull(row.get("end_datetime"), "D45 requires NOT_STARTED.end_datetime=null");
                });
    }
    private static void assertWithin(Object value, long before, long after, String label) {
        long observed = SchedulerAssertions.number(value);
        assertTrue(observed >= before && observed <= after,
                label + " must fall within the real DB-clock observation interval");
    }
    private static int timeout() {
        return new StandSettings().integer("workloads.scheduler.regression.timeout.seconds", 240, 30, 600);
    }
    private static long cleanupAgeMillis(boolean hung) {
        StandSettings stand = new StandSettings();
        String key = "workloads.scheduler.regression." + (hung ? "execution-timeout.millis" : "overdue.minutes");
        String raw = stand.optional(key, null);
        if (raw == null || raw.isBlank()) return 31L * DAY;
        long configured = Long.parseLong(raw.trim());
        if (configured <= 0 || configured > (hung ? 30 * DAY : 43_200L))
            throw new IllegalArgumentException("Expected a confirmed bounded scheduler cleanup threshold");
        return Math.addExact(hung ? configured : Math.multiplyExact(configured, 60_000L), DAY);
    }
    private static void attach(String title, SchedulerDbSteps db, SchedulerTestDataFactory data) {
        Allure.addAttachment(title, steps.rest.scheduler.SchedulerSteps.JSON.valueToTree(db.tasks(data.owner)).toPrettyString());
    }
}
