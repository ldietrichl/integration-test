package steps.flow.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import request.scheduler.SchedulerTestDataFactory;
import steps.db.scheduler.SchedulerDbSteps;
import steps.rest.scheduler.SchedulerSteps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static steps.rest.scheduler.SchedulerSteps.expect;
import static util.scheduler.SchedulerAssertions.*;

/** Live scheduler/users/dictionaries/DB only. No external controller or observation files. */
public final class SchedulerAutonomousRegressionSteps extends SchedulerScenarioSupport {
    private static final long SAFE_CLOCK_GAP_MILLIS = 3_600_000L;

    public void sch014() {
        if (!"deny-or-empty".equals(infrastructure.scheduler.SchedulerRegressionPolicy.anonymousContract()))
            throw new org.opentest4j.TestAbortedException("MY_TASKS_HEADER_FREE_CONTRACT_UNCONFIRMED");
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        List<Long> witnesses = new ArrayList<>();
        List<List<Map<String, Object>>> before = new ArrayList<>();
        getFlowWithDbRest()
                .step("Register controlled witnesses; the confirmed header-free contract is deny-or-empty", flow ->
                        prepareFixture(flow, data, 14))
                .step("Create two distinct authors' tasks without inferring an anonymous principal", flow -> {
                    witnesses.add(seedTask(flow, data, 1, "PLANNED"));
                    witnesses.add(seedTask(flow, data, 1, "PLANNED", data.objectId, "EXP", "START",
                            settings().otherUser(), data.future));
                    before.add(flow.dbCustomSteps().schedulerSteps().tasks(data.owner));
                    assertEquals(2, before.get(0).size());
                })
                .step("Remove application headers and check the agreed no-user contract", flow -> {
                    var request = data.registry();
                    request.put("presetFilter", Map.of("taskScope", "MY_TASKS"));
                    var response = flow.restCustomSteps().schedulerSteps().registryWithoutIdentity(request);
                    int status = response.toResponse().statusCode();
                    steps.container.KubernetesTunnelSteps.evidence("Header-free MY_TASKS observation", Map.of(
                            "contract", "deny-or-empty", "fixtureIds", witnesses, "status", status,
                            "mtlsIdentityRemoved", false, "applicationPrincipalIndependentlyVerified", false));
                    assertTrue(status == 200 || status == 401 || status == 403,
                            "Confirmed header-free contract: reject or empty; HTTP " + status);
                    if (status == 200)
                        assertTrue(content(expect(response, 200)).isEmpty(),
                                "No returned row is removed locally: header-free MY_TASKS disclosed a task");
                })
                .step("Check all controlled rows remain unchanged", flow ->
                        assertEquals(before.get(0), flow.dbCustomSteps().schedulerSteps().tasks(data.owner)))
                .run();
    }

    /** Core D10 behavior on real time, not the fixed-clock +/-1ms SCH-015 case. */
    public void live015() {
        presetOnRealClock(true);
    }

    /** Core D11 behavior on real time, not the fixed-clock boundary SCH-017 case. */
    public void live017() {
        presetOnRealClock(false);
    }

    private void presetOnRealClock(boolean nearest) {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        List<Long> expected = new ArrayList<>();
        List<Long> expectedDates = new ArrayList<>();
        List<List<Map<String, Object>>> before = new ArrayList<>();
        long[] anchor = new long[1];
        String preset = nearest ? "NEAREST_PLANNED" : "LAST_ERRORS";
        String status = nearest ? "PLANNED" : "ERROR";

        getFlowWithDbRest()
                .step("Register isolated fixtures with the scheduler jobs actually paused", flow ->
                        prepareFixture(flow, data, nearest ? 15 : 17))
                .step("Read the database clock and create positive and negative preset witnesses", flow -> {
                    anchor[0] = databaseTime(flow.dbCustomSteps().schedulerSteps());
                    // IDs deliberately oppose the required temporal order: a plain id DESC must fail.
                    long first = anchor[0] + (nearest ? SAFE_CLOCK_GAP_MILLIS : -SAFE_CLOCK_GAP_MILLIS);
                    long second = anchor[0] + (nearest ? 2 * SAFE_CLOCK_GAP_MILLIS : -2 * SAFE_CLOCK_GAP_MILLIS);
                    expected.add(seedTask(flow, data, 1, status, data.objectId, "EXP", "START",
                            settings().user(), first));
                    expected.add(seedTask(flow, data, 1, status, data.objectId, "EXP", "START",
                            settings().user(), second));
                    expectedDates.add(first);
                    expectedDates.add(second);
                    seedTask(flow, data, 1, status, data.objectId, "EXP", "START", settings().user(),
                            anchor[0] + (nearest ? -SAFE_CLOCK_GAP_MILLIS : SAFE_CLOCK_GAP_MILLIS));
                    seedTask(flow, data, 1, nearest ? "ERROR" : "PLANNED", data.objectId,
                            "EXP", "START", settings().user(), first);
                    before.add(flow.dbCustomSteps().schedulerSteps().tasks(data.owner));
                    assertEquals(4, before.get(0).size());
                    io.qameta.allure.Allure.addAttachment("Real-clock preset scope", "application/json",
                            SchedulerSteps.JSON.valueToTree(Map.of(
                                    "preset", preset, "databaseClockMillis", anchor[0],
                                    "safeGapMillis", SAFE_CLOCK_GAP_MILLIS,
                                    "expectedIds", expected, "expectedDates", expectedDates,
                                    "coverage", "Core filter/order only; fixed-clock boundaries are NOT covered")).toString());
                })
                .step("Request the preset without explicit sorts and check exact rows and order", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("presetFilter", Map.of("taskStatus", preset));
                    List<JsonNode> rows = content(expect(
                            flow.restCustomSteps().schedulerSteps().registry(request), 200));
                    long observedNow = databaseTime(flow.dbCustomSteps().schedulerSteps());
                    if (observedNow <= anchor[0] - SAFE_CLOCK_GAP_MILLIS / 2
                            || observedNow >= anchor[0] + SAFE_CLOCK_GAP_MILLIS / 2)
                        throw new IllegalStateException("DB clock moved outside the preset fixture safety window");
                    assertEquals(2, rows.size(), "Preset must exclude wrong statuses and the wrong time side");
                    assertEquals(expected, rows.stream().map(row -> id(row)).toList(),
                            "Preset temporal order must not be replaced by id DESC");
                    for (int i = 0; i < rows.size(); i++) {
                        JsonNode row = rows.get(i);
                        assertEquals(status, row.at("/status/code").asText());
                        assertDocumentedEpochInteger(row.path("planDt"), preset + ".planDt");
                        long actualDate = row.path("planDt").longValue();
                        assertEquals(expectedDates.get(i).longValue(), actualDate);
                        assertTrue(nearest ? actualDate > observedNow : actualDate < observedNow);
                    }
                })
                .step("Assert preset lookup left all owned DB rows unchanged", flow ->
                        assertEquals(before.get(0), flow.dbCustomSteps().schedulerSteps().tasks(data.owner)))
                .run();
    }

    private long databaseTime(SchedulerDbSteps db) {
        List<Map<String, Object>> result = db.query(
                "SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint AS utc_millis");
        assertEquals(1, result.size(), "One PostgreSQL clock sample required");
        long time = number(result.get(0).get("utc_millis"));
        assertTrue(time > 0, "Positive UTC epoch milliseconds required");
        return time;
    }
}
