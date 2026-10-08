package steps.flow.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.special.EnvironmentConfigWithScheduler;
import config.services.core.StandSettings;
import constants.Endpoints.Scheduler;
import flow.DbCustomFlow;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.scheduler.SchedulerTestDataFactory;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.scheduler.AbstractSchedulerFlowTest;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.scheduler.SchedulerAssertions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static request.scheduler.SchedulerTestDataFactory.filter;
import static request.scheduler.SchedulerTestDataFactory.firstAction;
import static steps.rest.scheduler.SchedulerSteps.expect;
import static util.scheduler.SchedulerAssertions.*;


/** Source-backed reusable flow steps; no JUnit scenario discovery here. */
public final class SchedulerApiRegressionSteps extends SchedulerScenarioSupport {


    public void sch002() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] taskId = new long[1];
        long[] legacyId = new long[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 2))
                .step("Create a V2 witness and only schema-compatible legacy control", flow -> {
                    taskId[0] = seedTask(flow, data, 1, "PLANNED");
                    boolean nullableVersion = "legacy-null".equals(settings().optional("schema.profile", "dev-v2"));
                    if (nullableVersion) legacyId[0] = seedTask(flow, data, null, "PLANNED");
                    steps.container.KubernetesTunnelSteps.evidence("Legacy control schema boundary", Map.of(
                            "schemaProfile", settings().optional("schema.profile", "dev-v2"),
                            "legacyControlCreated", nullableVersion,
                            "v2IsolationAssertionRequired", true,
                            "boundary", "No schema alteration; dev-v2 forbids a version-null fixture"));
                })
                .step("Prove the owned V2 task exists and is readable through the V2 registry", flow ->
                        assertEquals(Set.of(taskId[0]), ids(content(expect(
                                flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200)))))
                .step("Check the positive legacy control when the selected schema permits it", flow -> {
                    if (legacyId[0] > 0) {
                        JsonNode legacy = expect(flow.restCustomSteps().schedulerSteps().getV1(legacyId[0]), 200);
                        assertEquals(legacyId[0], legacy.path("id").asLong(), "Legacy control id");
                    }
                })
                .step("Reject the V2 task with documented data-not-found status, not an auth/gateway failure", flow -> {
                    // ExpLab v17, PDF p.2586: data not found -> 422. Always asserted on DEV too.
                    expect(flow.restCustomSteps().schedulerSteps().getV1(taskId[0]), 422);
                })
                .run();
    }

    public void sch008() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        Set<Long> seededIds = new LinkedHashSet<>();

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 8))
                .step("Create 23 tasks for pagination", flow -> {
                    for (int index = 0; index < 23; index++) {
                        seededIds.add(seedTask(flow, data, 1, "PLANNED"));
                    }
                })
                .step("Request the selected page and check page boundaries", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("page", 0);
                    request.put("size", 10);
                    JsonNode response = expect(flow.restCustomSteps().schedulerSteps().registry(request), 200);
                    List<JsonNode> rows = content(response);
                    assertEquals(3, response.path("totalPages").asInt(-1));
                    assertEquals(10, rows.size());
                    assertTrue(seededIds.containsAll(ids(rows)));
                })
                .run();
    }

    public void sch009() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        Set<Long> seededIds = new LinkedHashSet<>();

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 9))
                .step("Create 23 tasks for pagination", flow -> {
                    for (int index = 0; index < 23; index++) {
                        seededIds.add(seedTask(flow, data, 1, "PLANNED"));
                    }
                })
                .step("Request the selected page and check page boundaries", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("page", 2);
                    request.put("size", 10);
                    JsonNode response = expect(flow.restCustomSteps().schedulerSteps().registry(request), 200);
                    List<JsonNode> rows = content(response);
                    assertEquals(3, response.path("totalPages").asInt(-1));
                    assertEquals(3, rows.size());
                    assertTrue(seededIds.containsAll(ids(rows)));
                })
                .run();
    }

    public void sch010() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        Set<Long> seededIds = new LinkedHashSet<>();

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 10))
                .step("Create 23 tasks for pagination", flow -> {
                    for (int index = 0; index < 23; index++) {
                        seededIds.add(seedTask(flow, data, 1, "PLANNED"));
                    }
                })
                .step("Request the selected page and check page boundaries", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("page", 3);
                    request.put("size", 10);
                    JsonNode response = expect(flow.restCustomSteps().schedulerSteps().registry(request), 200);
                    List<JsonNode> rows = content(response);
                    assertEquals(3, response.path("totalPages").asInt(-1));
                    assertEquals(0, rows.size());
                    assertTrue(seededIds.containsAll(ids(rows)));
                })
                .run();
    }

    public void sch011() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        Set<Long> seededIds = new LinkedHashSet<>();

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 11))
                .step("Request the selected page and check page boundaries", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("page", 0);
                    request.put("size", 10);
                    JsonNode response = expect(flow.restCustomSteps().schedulerSteps().registry(request), 200);
                    List<JsonNode> rows = content(response);
                    assertEquals(0, response.path("totalPages").asInt(-1));
                    assertEquals(0, rows.size());
                    assertTrue(seededIds.containsAll(ids(rows)));
                })
                .run();
    }

    public void sch012() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        Set<Long> expectedIds = new HashSet<>();

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 12))
                .step("Create tasks for AND and OR filter groups", flow -> {
                    expectedIds.add(seedTask(flow, data, 1, "PLANNED"));
                    seedTask(flow, data, 1, "PLANNED", data.objectId, "EXP", "STOP", settings().user(), data.future);
                    expectedIds.add(seedTask(flow, data, 1, "ERROR", data.objectId, "EXP", "STOP", settings().user(), data.future));
                })
                .step("Check conjunction within a group and disjunction between groups", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("filters", List.of(
                            List.of(filter("objectId", "equal", Long.toString(data.objectId)),
                                    flow.restCustomSteps().schedulerRegistrySteps().exactFilter("status", "PLANNED"),
                                    flow.restCustomSteps().schedulerRegistrySteps().exactFilter("action", "START")),
                            List.of(filter("objectId", "equal", Long.toString(data.objectId)),
                                    flow.restCustomSteps().schedulerRegistrySteps().exactFilter("status", "ERROR"),
                                    flow.restCustomSteps().schedulerRegistrySteps().exactFilter("action", "STOP"))));
                    assertEquals(expectedIds, ids(content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200))));
                })
                .run();
    }

    public void sch013() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] mine = new long[1];
        long[] other = new long[1];
        List<List<Map<String, Object>>> before = new ArrayList<>();
        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 13))
                .step("Create two authors' witnesses using the resolved application user, never an anonymous create author", flow -> {
                    config.services.core.SchedulerApplicationIdentity.requireUser(settings().user());
                    long primary = settings().user();
                    long foreign = settings().otherUser();
                    assertNotEquals(primary, foreign);
                    mine[0] = seedTask(flow, data, 1, "PLANNED");
                    other[0] = seedTask(flow, data, 1, "PLANNED", data.objectId, "EXP", "START", foreign, data.future);
                    before.add(flow.dbCustomSteps().schedulerSteps().tasks(data.owner));
                    steps.container.KubernetesTunnelSteps.evidence("MY_TASKS identity and isolated witnesses", Map.of(
                            "mode", "application-token", "resolvedUserId", primary, "controlUserId", foreign,
                            "mineId", mine[0], "foreignId", other[0],
                            "jwtSignatureVerifiedLocally", false,
                            "registryPrincipalIndependentlyVerified", false,
                            "identityBoundary", "User-service resolves token sub; registry authorization is tested, not presumed"));
                })
                .step("Prove both controlled tasks are visible before applying MY_TASKS", flow -> {
                    var rows = content(expect(flow.restCustomSteps().schedulerSteps().registryWithIdentity(
                            data.registry(), config.services.core.SchedulerApplicationIdentity.requireToken()), 200));
                    assertEquals(2, rows.size(), "Both witnesses must exist; missing data is not privacy protection");
                    assertEquals(Set.of(mine[0], other[0]), ids(rows));
                })
                .step("Check the complete scoped MY_TASKS response without client-side removal of foreign rows", flow -> {
                    var request = data.registry();
                    request.put("presetFilter", Map.of("taskScope", "MY_TASKS"));
                    var rows = content(expect(flow.restCustomSteps().schedulerSteps().registryWithIdentity(
                            request, config.services.core.SchedulerApplicationIdentity.requireToken()), 200));
                    assertAll("MY_TASKS exact own witness and unchanged persistence",
                            () -> assertEquals(1, rows.size()),
                            () -> assertEquals(Set.of(mine[0]), ids(rows), "The other author's witness must not be returned"),
                            () -> assertEquals(before.get(0), flow.dbCustomSteps().schedulerSteps().tasks(data.owner)));
                }).run();
    }

    public void sch016() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 16))
                .step("Create an ERROR task", flow -> {
                    seedTask(flow, data, 1, "ERROR");
                })
                .step("Combine incompatible preset and explicit status filters", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("presetFilter", Map.of("taskStatus", "NEAREST_PLANNED"));
                    request.put("filters", List.of(List.of(
                            filter("objectId", "equal", Long.toString(data.objectId)),
                            flow.restCustomSteps().schedulerRegistrySteps().exactFilter("status", "ERROR"))));
                    assertTrue(content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200)).isEmpty());
                })
                .run();
    }

    public void sch018() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] taskIds = new long[2];
        String[] targetName = new String[1];
        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 18))
                .step("Create distinct named witnesses and bind the name to its exact task ID", flow -> {
                    taskIds[0] = seedTask(flow, data, 1, "PLANNED");
                    taskIds[1] = seedTask(flow, data, 1, "PLANNED");
                    var rows = flow.dbCustomSteps().schedulerSteps().tasks(data.owner);
                    targetName[0] = rows.stream().filter(row -> ((Number) row.get("id")).longValue() == taskIds[0])
                            .map(row -> (String) row.get("object_name")).findFirst().orElseThrow();
                    assertEquals(2, rows.stream().map(row -> row.get("object_name")).distinct().count());
                })
                .step("Verify both controls exist without search", flow -> {
                    assertEquals(Set.of(taskIds[0], taskIds[1]), ids(content(expect(
                            flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200))));
                    steps.container.KubernetesTunnelSteps.evidence("SCH-018 search contract boundary", Map.of(
                            "reference", "ExpLab v17 p2641: search is a string; searchable fields are not enumerated",
                            "hypothesis", "object_name is searchable", "fieldScopeConfirmed", false,
                            "expectedTaskId", taskIds[0], "excludedTaskId", taskIds[1],
                            "note", "Both probes stay strict and independent; failure needs search-scope confirmation"));
                })
                .step("Run positive and absent-name search independently", flow -> {
                    var before = flow.dbCustomSteps().schedulerSteps().tasks(data.owner);
                    assertAll("Search hypothesis and read-only persistence",
                            () -> {
                                Map<String, Object> request = data.registry();
                                request.put("search", targetName[0]);
                                assertEquals(Set.of(taskIds[0]), ids(content(expect(
                                        flow.restCustomSteps().schedulerSteps().registry(request), 200))),
                                        "SEARCH_SCOPE_UNCONFIRMED: exact owned object-name witness");
                            },
                            () -> {
                                Map<String, Object> request = data.registry();
                                request.put("search", data.owner + "NO_MATCH");
                                assertTrue(content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200)).isEmpty(),
                                        "SEARCH_SCOPE_UNCONFIRMED: an absent name must not return owned witnesses");
                            },
                            () -> assertEquals(before, flow.dbCustomSteps().schedulerSteps().tasks(data.owner),
                                    "Search must not modify owned tasks"));
                }).run();
    }

    public void sch019() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        List<Long> seededIds = new ArrayList<>();

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 19))
                .step("Create tasks with deterministic sort keys", flow -> {
                    for (int offset : new int[]{2, 0, 1}) {
                        long taskId = seedTask(flow, data, 1, "PLANNED", data.objectId, "EXP", "START", settings().user(), data.future + offset * 60000L);
                        seededIds.add(taskId);
                    }
                })
                .step("Check sort order, secondary key and repeatability", flow -> {
                    Map<String, Object> request = data.registry();
                    List<Map<String, String>> sorts = new ArrayList<>();
                    sorts.add(Map.of("code", "scheduleDatetime", "direction", "ASC"));
                    request.put("sorts", sorts);
                    List<JsonNode> rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200));
                    assertEquals(3, rows.size());
                    for (int index = 1; index < rows.size(); index++) {
                        int order = compareRegistryTime(rows.get(index - 1), rows.get(index), "planDt");
                        assertTrue(order <= 0, "Incorrect ASC sort order");
                    }
                    assertEquals(new HashSet<>(seededIds), ids(rows));
                    assertEquals(rows, content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200)));
                })
                .run();
    }

    public void sch020() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        List<Long> seededIds = new ArrayList<>();

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 20))
                .step("Create tasks with deterministic sort keys", flow -> {
                    for (int offset : new int[]{2, 0, 1}) {
                        long taskId = seedTask(flow, data, 1, "PLANNED", data.objectId, "EXP", "START", settings().user(), data.future + offset * 60000L);
                        seededIds.add(taskId);
                    }
                })
                .step("Check sort order, secondary key and repeatability", flow -> {
                    Map<String, Object> request = data.registry();
                    List<Map<String, String>> sorts = new ArrayList<>();
                    sorts.add(Map.of("code", "scheduleDatetime", "direction", "DESC"));
                    request.put("sorts", sorts);
                    List<JsonNode> rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200));
                    assertEquals(3, rows.size());
                    for (int index = 1; index < rows.size(); index++) {
                        int order = compareRegistryTime(rows.get(index - 1), rows.get(index), "planDt");
                        assertTrue(order >= 0, "Incorrect DESC sort order");
                    }
                    assertEquals(new HashSet<>(seededIds), ids(rows));
                    assertEquals(rows, content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200)));
                })
                .run();
    }

    public void sch021() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        List<Long> seededIds = new ArrayList<>();

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 21))
                .step("Create tasks with deterministic sort keys", flow -> {
                    for (int offset : new int[]{2, 0, 0}) {
                        long taskId = seedTask(flow, data, 1, "PLANNED", data.objectId, "EXP", "START", settings().user(), data.future + offset * 60000L);
                        seededIds.add(taskId);
                        flow.dbCustomSteps().schedulerSteps().setOwnedCreatedAt(data.owner, taskId, data.now + seededIds.size() * 1000L);
                    }
                })
                .step("Check sort order, secondary key and repeatability", flow -> {
                    Map<String, Object> request = data.registry();
                    List<Map<String, String>> sorts = new ArrayList<>();
                    sorts.add(Map.of("code", "scheduleDatetime", "direction", "ASC"));
                    sorts.add(Map.of("code", "createdAt", "direction", "DESC"));
                    request.put("sorts", sorts);
                    List<JsonNode> rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200));
                    assertEquals(3, rows.size());
                    for (int index = 1; index < rows.size(); index++) {
                        int order = compareRegistryTime(rows.get(index - 1), rows.get(index), "planDt");
                        assertTrue(order <= 0, "Incorrect ASC sort order");
                    }
                    assertEquals(new HashSet<>(seededIds), ids(rows));
                    assertEquals(List.of(seededIds.get(2), seededIds.get(1), seededIds.get(0)),
                            rows.stream().map(SchedulerAssertions::id).collect(Collectors.toList()),
                            "Secondary createdAt DESC must order equal scheduleDatetime values");
                    assertEquals(rows, content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200)));
                })
                .run();
    }

    public void sch022() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 22))
                .step("Send invalid pagination and require HTTP 400", flow -> {
                    Map<String, Object> request = data.registry();
                    request.remove("page");
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }

    public void sch023() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 23))
                .step("Send invalid pagination and require HTTP 400", flow -> {
                    Map<String, Object> request = data.registry();
                    request.remove("size");
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }

    public void sch024() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 24))
                .step("Send invalid pagination and require HTTP 400", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("page", -1);
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }

    public void sch025() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 25))
                .step("Send invalid pagination and require HTTP 400", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("size", 0);
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }

    public void sch026() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 26))
                .step("Send invalid pagination and require HTTP 400", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("size", -1);
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }

    public void sch027() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 27))
                .step("Send invalid pagination and require HTTP 400", flow -> {
                    Map<String, Object> request = data.registry();
                    request.put("page", "abc");
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }

    public void sch029() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] taskId = new long[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 29))
                .step("Create a task for creator enrichment", flow -> {
                    taskId[0] = seedTask(flow, data, 1, "PLANNED");
                })
                .step("Check the creator returned by the real user-service integration", flow -> {
                    List<JsonNode> rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200));
                    assertEquals(Set.of(taskId[0]), ids(rows));
                    JsonNode creator = rows.get(0).get("creator");
                    assertNotNull(creator);
                    // Expected creator identity is taken from the same stand DB that backs the
                    // enrichment integration, not invented by the test. An explicit operator
                    // override (scheduler.<env>.expected-creator.*) still wins when provided.
                    long creatorId = rows.get(0).path("createdBy").asLong();
                    Map<String, Object> expected = creatorIdentity(flow, creatorId);
                    assertEquals(expected.get("employeeId"), creator.path("employeeId").asText());
                    if (expected.containsKey("email"))
                        assertEquals(expected.get("email"), creator.path("email").asText());
                    else
                        assertTrue(creator.path("email").isTextual() && !creator.path("email").asText().isBlank(),
                                "creator.email must be populated by the user-service integration");
                })
                .run();
    }

    public void sch030() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 30))
                .step("Create a task with the configured splitting point", flow -> {
                    seedTask(flow, data, 1, "PLANNED");
                })
                .step("Check splitting point dictionary enrichment", flow -> {
                    JsonNode point = content(expect(flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200)).get(0).path("splittingPoint");
                    assertEquals(settings().splittingPoint(), point.path("code").asText());
                    String name = point.path("name").asText();
                    String dictionaryName = flow.restCustomSteps().schedulerRegistrySteps()
                            .splittingPointName(settings().splittingPoint());
                    assertEquals(dictionaryName, name,
                            "splittingPoint.name must equal the current V2 dictionary name");
                    JsonNode again = content(expect(flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200)).get(0).path("splittingPoint");
                    assertEquals(name, again.path("name").asText(), "splittingPoint.name must be stable across calls");
                    String configured = settings().optional("splitting-point-name", null);
                    if (configured != null)
                        assertEquals(settings().required("splitting-point-name"), name);
                })
                .run();
    }

    public void sch031() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] taskId = new long[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 31))
                .step("Create a task in the required status", flow -> {
                    taskId[0] = seedTask(flow, data, 1, "PLANNED");
                })
                .step("Check status code, display name, creator and epoch time", flow -> {
                    List<JsonNode> rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200));
                    assertEquals(Set.of(taskId[0]), ids(rows));
                    JsonNode value = rows.get(0);
                    assertEquals("PLANNED", value.at("/status/code").asText());
                    assertEquals("Запланировано", value.at("/status/name").asText());
                    assertEquals(settings().user(), value.path("createdBy").asLong());
                    assertTrue(value.path("planDt").isIntegralNumber());
                    assertEquals(data.future, value.path("planDt").asLong());
                })
                .run();
    }

    public void sch032() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] taskId = new long[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 32))
                .step("Create a task in the required status", flow -> {
                    taskId[0] = seedTask(flow, data, 1, "IN_PROGRESS");
                })
                .step("Check status code, display name, creator and epoch time", flow -> {
                    List<JsonNode> rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200));
                    assertEquals(Set.of(taskId[0]), ids(rows));
                    JsonNode value = rows.get(0);
                    assertEquals("IN_PROGRESS", value.at("/status/code").asText());
                    assertEquals("Выполняется", value.at("/status/name").asText());
                    assertEquals(settings().user(), value.path("createdBy").asLong());
                    assertTrue(value.path("planDt").isIntegralNumber());
                    assertEquals(data.future, value.path("planDt").asLong());
                })
                .run();
    }

    public void sch033() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] taskId = new long[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 33))
                .step("Create a task in the required status", flow -> {
                    taskId[0] = seedTask(flow, data, 1, "COMPLETED");
                })
                .step("Check status code, display name, creator and epoch time", flow -> {
                    List<JsonNode> rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200));
                    assertEquals(Set.of(taskId[0]), ids(rows));
                    JsonNode value = rows.get(0);
                    assertEquals("COMPLETED", value.at("/status/code").asText());
                    assertEquals("Завершено", value.at("/status/name").asText());
                    assertEquals(settings().user(), value.path("createdBy").asLong());
                    assertTrue(value.path("planDt").isIntegralNumber());
                    assertEquals(data.future, value.path("planDt").asLong());
                })
                .run();
    }

    public void sch034() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] taskId = new long[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 34))
                .step("Create a task in the required status", flow -> {
                    taskId[0] = seedTask(flow, data, 1, "ERROR");
                })
                .step("Check status code, display name, creator and epoch time", flow -> {
                    List<JsonNode> rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200));
                    assertEquals(Set.of(taskId[0]), ids(rows));
                    JsonNode value = rows.get(0);
                    assertEquals("ERROR", value.at("/status/code").asText());
                    assertEquals("Ошибка", value.at("/status/name").asText());
                    assertEquals(settings().user(), value.path("createdBy").asLong());
                    assertTrue(value.path("planDt").isIntegralNumber());
                    assertEquals(data.future, value.path("planDt").asLong());
                })
                .run();
    }

    public void sch035() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] taskId = new long[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 35))
                .step("Create a task in the required status", flow -> {
                    taskId[0] = seedTask(flow, data, 1, "NOT_STARTED");
                })
                .step("Check status code, display name, creator and epoch time", flow -> {
                    List<JsonNode> rows = content(expect(flow.restCustomSteps().schedulerSteps().registry(data.registry()), 200));
                    assertEquals(Set.of(taskId[0]), ids(rows));
                    JsonNode value = rows.get(0);
                    assertEquals("NOT_STARTED", value.at("/status/code").asText());
                    assertEquals("Пропущено", value.at("/status/name").asText());
                    assertEquals(settings().user(), value.path("createdBy").asLong());
                    assertTrue(value.path("planDt").isIntegralNumber());
                    assertEquals(data.future, value.path("planDt").asLong());
                })
                .run();
    }

    public void sch036() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 36))
                .step("Reject an incomplete creation request without persisting tasks", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    request.remove("splittingPointCode");
                    ValidatableResponseWrapper response = flow.restCustomSteps().schedulerSteps().createV2(request);
                    assertAll("Invalid creation must reject the request and leave no tasks",
                            () -> expect(response, 400),
                            () -> shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0));
                })
                .run();
    }

    public void sch037() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 37))
                .step("Reject an incomplete creation request without persisting tasks", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    request.remove("scheduleDateTime");
                    ValidatableResponseWrapper response = flow.restCustomSteps().schedulerSteps().createV2(request);
                    assertAll("Invalid creation must reject the request and leave no tasks",
                            () -> expect(response, 400),
                            () -> shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0));
                })
                .run();
    }

    public void sch038() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 38))
                .step("Reject an incomplete creation request without persisting tasks", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    request.remove("createdBy");
                    ValidatableResponseWrapper response = flow.restCustomSteps().schedulerSteps().createV2(request);
                    assertAll("Invalid creation must reject the request and leave no tasks",
                            () -> expect(response, 400),
                            () -> shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0));
                })
                .run();
    }

    public void sch039() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 39))
                .step("Reject an incomplete creation request without persisting tasks", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    request.remove("actions");
                    ValidatableResponseWrapper response = flow.restCustomSteps().schedulerSteps().createV2(request);
                    assertAll("Invalid creation must reject the request and leave no tasks",
                            () -> expect(response, 400),
                            () -> shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0));
                })
                .run();
    }

    public void sch040() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 40))
                .step("Reject an incomplete creation request without persisting tasks", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    firstAction(request).remove("objectType");
                    ValidatableResponseWrapper response = flow.restCustomSteps().schedulerSteps().createV2(request);
                    assertAll("Invalid creation must reject the request and leave no tasks",
                            () -> expect(response, 400),
                            () -> shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0));
                })
                .run();
    }

    public void sch041() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 41))
                .step("Reject an incomplete creation request without persisting tasks", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    firstAction(request).remove("objectId");
                    ValidatableResponseWrapper response = flow.restCustomSteps().schedulerSteps().createV2(request);
                    assertAll("Invalid creation must reject the request and leave no tasks",
                            () -> expect(response, 400),
                            () -> shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0));
                })
                .run();
    }

    public void sch042() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 42))
                .step("Reject an incomplete creation request without persisting tasks", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    firstAction(request).remove("objectName");
                    ValidatableResponseWrapper response = flow.restCustomSteps().schedulerSteps().createV2(request);
                    assertAll("Invalid creation must reject the request and leave no tasks",
                            () -> expect(response, 400),
                            () -> shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0));
                })
                .run();
    }

    public void sch043() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 43))
                .step("Reject an incomplete creation request without persisting tasks", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    firstAction(request).remove("action");
                    ValidatableResponseWrapper response = flow.restCustomSteps().schedulerSteps().createV2(request);
                    assertAll("Invalid creation must reject the request and leave no tasks",
                            () -> expect(response, 400),
                            () -> shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0));
                })
                .run();
    }

    public void sch044() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 44))
                .step("Reject an incomplete creation request without persisting tasks", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    request.put("actions", List.of());
                    ValidatableResponseWrapper response = flow.restCustomSteps().schedulerSteps().createV2(request);
                    assertAll("Invalid creation must reject the request and leave no tasks",
                            () -> expect(response, 400),
                            () -> shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0));
                })
                .run();
    }

    public void sch045() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        Map<String, Object> request = data.createTaskV2();
        JsonNode[] response = new JsonNode[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 45))
                .step("Create a v2 task through project REST steps", flow -> {
                    response[0] = expect(flow.restCustomSteps().schedulerSteps().createV2(request), 200);
                })
                .step("Check the persisted status, version and timestamp", flow -> {
                    Map<String, Object> task = flow.dbCustomSteps().schedulerSteps().task(id(response[0]));
                    assertEquals("PLANNED", task.get("status"));
                    assertEquals(1, task.get("version"));
                    assertEquals(number(request.get("scheduleDateTime")), number(task.get("schedule_datetime")));
                })
                .run();
    }

    public void sch046() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 46))
                .step("Create one task with two actions", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    request.put("actions", List.of(
                            data.action("EXP", data.objectId, "START"), data.action("EXP", data.objectId + 1, "STOP")));
                    expect(flow.restCustomSteps().schedulerSteps().createV2(request), 200);
                })
                .step("Ensure both actions belong to the same task", flow -> {
                    List<Map<String, Object>> rows = flow.dbCustomSteps().schedulerSteps().tasks(data.owner);
                    assertEquals(2, rows.size());
                    assertEquals(1, rows.stream().map(row -> row.get("id")).distinct().count(),
                            "One task must own both actions");
                })
                .run();
    }

    public void sch048() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        Map<String, Object> request = data.createTaskV2();
        JsonNode[] response = new JsonNode[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 48))
                .step("Create a v2 task through project REST steps", flow -> {
                    response[0] = expect(flow.restCustomSteps().schedulerSteps().createV2(request), 200);
                })
                .step("Check the persisted status, version and timestamp and response DTO", flow -> {
                    Map<String, Object> task = flow.dbCustomSteps().schedulerSteps().task(id(response[0]));
                    assertEquals("PLANNED", task.get("status"));
                    assertEquals(1, task.get("version"));
                    assertEquals(number(request.get("scheduleDateTime")), number(task.get("schedule_datetime")));
                    assertEquals(1, response[0].path("version").asInt(-1));
                    assertTrue(response[0].hasNonNull("createdAt"));
                    assertEquals(settings().splittingPoint(), response[0].at("/splittingPoint/code").asText());
                    assertTrue(response[0].hasNonNull("status"));
                })
                .run();
    }

    public void sch049() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 49))
                .step("Create a task for the selected object type", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    firstAction(request).put("objectType", "EXP");
                    JsonNode response = expect(flow.restCustomSteps().schedulerSteps().createV2(request), 200);
                    assertTrue(id(response) > 0);
                })
                .step("Check the stored object type", flow -> {
                    List<Map<String, Object>> rows = flow.dbCustomSteps().schedulerSteps().tasks(data.owner);
                    assertEquals(1, rows.size());
                    assertEquals("EXP", rows.get(0).get("object_type"));
                })
                .run();
    }

    public void sch050() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 50))
                .step("Create a task for the selected object type", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    firstAction(request).put("objectType", "CJ");
                    JsonNode response = expect(flow.restCustomSteps().schedulerSteps().createV2(request), 200);
                    assertTrue(id(response) > 0);
                })
                .step("Check the stored object type", flow -> {
                    List<Map<String, Object>> rows = flow.dbCustomSteps().schedulerSteps().tasks(data.owner);
                    assertEquals(1, rows.size());
                    assertEquals("CJ", rows.get(0).get("object_type"));
                })
                .run();
    }

    public void sch051() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 51))
                .step("Create a task for the selected object type", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    firstAction(request).put("objectType", "SPLIT");
                    JsonNode response = expect(flow.restCustomSteps().schedulerSteps().createV2(request), 200);
                    assertTrue(id(response) > 0);
                })
                .step("Check the stored object type", flow -> {
                    List<Map<String, Object>> rows = flow.dbCustomSteps().schedulerSteps().tasks(data.owner);
                    assertEquals(1, rows.size());
                    assertEquals("SPLIT", rows.get(0).get("object_type"));
                })
                .run();
    }

    public void sch052() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 52))
                .step("Create a task for the selected object type", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    firstAction(request).put("objectType", "PILOT");
                    JsonNode response = expect(flow.restCustomSteps().schedulerSteps().createV2(request), 200);
                    assertTrue(id(response) > 0);
                })
                .step("Check the stored object type", flow -> {
                    List<Map<String, Object>> rows = flow.dbCustomSteps().schedulerSteps().tasks(data.owner);
                    assertEquals(1, rows.size());
                    assertEquals("PILOT", rows.get(0).get("object_type"));
                })
                .run();
    }

    public void sch053() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 53))
                .step("Reject an unknown action", flow -> {
                    Map<String, Object> request = data.createTaskV2();
                    firstAction(request).put("action", "UNKNOWN");
                    expect(flow.restCustomSteps().schedulerSteps().createV2(request), 400);
                })
                .step("Ensure no task was created", flow -> {
                    shouldHaveOwnedRowCount(flow.dbCustomSteps().schedulerSteps(), data.owner, 0);
                })
                .run();
    }

    public void sch054() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] plannedId = new long[1];
        Set<Long> keptIds = new HashSet<>();

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 54))
                .step("Create target and protected tasks", flow -> {
                    plannedId[0] = seedTask(flow, data, 1, "PLANNED");
                    for (String status : List.of("IN_PROGRESS", "COMPLETED", "ERROR", "NOT_STARTED")) {
                        keptIds.add(seedTask(flow, data, 1, status));
                    }
                    keptIds.add(seedTask(flow, data, 1, "PLANNED", data.objectId + 1, "EXP", "START", settings().user(), data.future));
                    keptIds.add(seedTask(flow, data, 1, "PLANNED", data.objectId, "PILOT", "START", settings().user(), data.future));
                })
                .step("Delete planned tasks for the target object", flow -> {
                    expect(flow.restCustomSteps().schedulerSteps().deletePlanned("EXP", data.objectId), 200);
                })
                .step("Ensure non-target objects, types and statuses are unchanged", flow -> {
                    Set<Long> actualIds = flow.dbCustomSteps().schedulerSteps().tasks(data.owner).stream()
                            .map(row -> number(row.get("id"))).collect(Collectors.toSet());
                    assertEquals(keptIds, actualIds);
                    assertFalse(actualIds.contains(plannedId[0]));
                })
                .run();
    }

    public void sch055() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        long[] deleteMillis = new long[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 55))
                .step("Create the 2000-row boundary and a further planned task", flow -> {
                    flow.dbCustomSteps().schedulerSteps().seedBatch(
                            data.owner + "Batch", 2000, 1, "PLANNED",
                            data.objectId, settings().user(), data.future, settings().splittingPoint());
                    seedTask(flow, data, 1, "PLANNED");
                })
                .step("Delete planned tasks beyond the first batch", flow -> {
                    long started = System.nanoTime();
                    try {
                        expect(flow.restCustomSteps().schedulerSteps().deletePlanned(
                                "EXP", data.objectId, settings().bulkTimeoutSeconds()), 200);
                    } finally {
                        deleteMillis[0] = java.util.concurrent.TimeUnit.NANOSECONDS
                                .toMillis(System.nanoTime() - started);
                        io.qameta.allure.Allure.addAttachment("SCH-055 bulk delete timing",
                                "elapsedMillis=" + deleteMillis[0]
                                        + "\nreadTimeoutSeconds=" + settings().bulkTimeoutSeconds());
                    }
                })
                .step("Check remaining rows and statuses", flow -> {
                    List<Map<String, Object>> rows = flow.dbCustomSteps().schedulerSteps().tasks(data.owner);
                    assertEquals(0, rows.size());
                    assertTrue(rows.stream().noneMatch(row -> "PLANNED".equals(row.get("status"))));
                })
                .run();
    }

    public void sch056() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 56))
                .step("Create the 2000-row boundary and a further planned task", flow -> {
                    flow.dbCustomSteps().schedulerSteps().seedBatch(
                            data.owner + "Batch", 2000, 1, "COMPLETED",
                            data.objectId, settings().user(), data.future, settings().splittingPoint());
                    seedTask(flow, data, 1, "PLANNED");
                })
                .step("Delete planned tasks beyond the first batch", flow -> {
                    expect(flow.restCustomSteps().schedulerSteps().deletePlanned("EXP", data.objectId), 200);
                })
                .step("Check remaining rows and statuses", flow -> {
                    List<Map<String, Object>> rows = flow.dbCustomSteps().schedulerSteps().tasks(data.owner);
                    assertEquals(2000, rows.size());
                    assertTrue(rows.stream().noneMatch(row -> "PLANNED".equals(row.get("status"))));
                })
                .run();
    }


    public void sch093() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 93))
                .step("Check malformed JSON independently on each v2 route", flow -> {
                    assertAll("Malformed JSON must be checked independently on every v2 route",
                            List.of(Scheduler.V2_TASK, Scheduler.V2_TASKS, Scheduler.V2_DELETE).stream()
                                    .map(path -> (Executable) () -> {
                                        JsonNode error = expect(flow.restCustomSteps().schedulerSteps().call("POST", path, "{"), 400);
                                        assertDoesNotThrow(() -> UUID.fromString(error.path("id").asText()));
                                        assertFalse(error.path("message").asText().isBlank());
                                    }));
                })
                .run();
    }








    public void sch126() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 126))
                .step("Reject an unknown registry filter code", flow -> {
                    Map<String, Object> request = data.registry();
                    Map<String, Object> invalidFilter = filter("objectId", "equal", Long.toString(data.objectId));
                    invalidFilter.put("code", "SCH_UNKNOWN_FIELD");
                    request.put("filters", List.of(List.of(invalidFilter)));
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }

    public void sch127() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 127))
                .step("Reject an invalid registry filter", flow -> {
                    Map<String, Object> request = data.registry();
                    Map<String, Object> invalidFilter = filter("objectId", "equal", Long.toString(data.objectId));
                    invalidFilter.put("operator", "like");
                    request.put("filters", List.of(List.of(invalidFilter)));
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }

    public void sch128() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 128))
                .step("Accept a registry filter with an empty values array", flow -> {
                    Map<String, Object> request = data.registry();
                    Map<String, Object> filterWithEmptyValues = filter(
                            "objectId", "equal", Long.toString(data.objectId));
                    filterWithEmptyValues.put("values", List.of());
                    request.put("filters", List.of(List.of(filterWithEmptyValues)));
                    content(expect(flow.restCustomSteps().schedulerSteps().registry(request), 200));
                })
                .run();
    }

    public void sch129() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 129))
                .step("Reject an invalid registry filter", flow -> {
                    Map<String, Object> request = data.registry();
                    Map<String, Object> invalidFilter = filter("objectId", "equal", Long.toString(data.objectId));
                    invalidFilter.put("operator", null);
                    request.put("filters", List.of(List.of(invalidFilter)));
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }

    public void sch130() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 130))
                .step("Reject an invalid registry filter", flow -> {
                    Map<String, Object> request = data.registry();
                    Map<String, Object> invalidFilter = filter("objectId", "equal", Long.toString(data.objectId));
                    invalidFilter.put("values", List.of("NOT_A_NUMBER"));
                    request.put("filters", List.of(List.of(invalidFilter)));
                    expect(flow.restCustomSteps().schedulerSteps().registry(request), 400);
                })
                .run();
    }



    public void sch134() {
        SchedulerTestDataFactory data = new SchedulerTestDataFactory(settings());
        Map<String, Object> request = data.createTaskV2();
        request.put("scheduleDateTime", data.future + 123L);
        JsonNode[] response = new JsonNode[1];

        getFlowWithDbRest()
                .step("Check schema and register owned scheduler fixtures", flow -> prepareFixture(flow, data, 134))
                .step("Create a v2 task through project REST steps", flow -> {
                    response[0] = expect(flow.restCustomSteps().schedulerSteps().createV2(request), 200);
                })
                .step("Check the persisted status, version and timestamp", flow -> {
                    Map<String, Object> task = flow.dbCustomSteps().schedulerSteps().task(id(response[0]));
                    assertEquals("PLANNED", task.get("status"));
                    assertEquals(1, task.get("version"));
                    assertEquals(number(request.get("scheduleDateTime")), number(task.get("schedule_datetime")));
                })
                .run();
    }

    public void sch135() {
        getFlowWithRest()
                .step("Read scheduler actuator endpoints through the internal service tunnel",
                        flow -> new SchedulerWorkloadDiagnosticSteps()
                                .assertHealthyAndPrometheus("sch-135"))
                .run();
    }

    /**
     * Expected creator identity for SCH-029. An explicit operator override
     * (scheduler.<env>.expected-creator.*) wins; otherwise the value is read from the
     * same stand user table that backs the service enrichment, so no per-stand hardcoding
     * is required. email is compared only when the stand user table exposes it.
     */
    private Map<String, Object> creatorIdentity(DbCustomFlow flow, long creatorId) {
        if (settings().optional("expected-creator.employee-id", null) != null
                || settings().optional("expected-creator.email", null) != null) {
            Map<String, Object> configured = new LinkedHashMap<>();
            if (settings().optional("expected-creator.employee-id", null) != null)
                configured.put("employeeId", settings().required("expected-creator.employee-id"));
            if (settings().optional("expected-creator.email", null) != null)
                configured.put("email", settings().required("expected-creator.email"));
            return configured;
        }
        String schema = new StandSettings().required("fixtures.users.schema");
        if (!schema.matches("[a-z][a-z0-9_]*"))
            throw new IllegalArgumentException("A simple approved user schema name is required");
        List<Map<String, Object>> columns = flow.dbCustomSteps().schedulerSteps().query(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = '" + schema + "' AND table_name = 'user'");
        Set<String> names = columns.stream()
                .map(row -> String.valueOf(row.get("column_name")))
                .collect(Collectors.toSet());
        if (!names.contains("employee_id")) {
            throw new IllegalStateException("users." + schema + ".user has no employee_id column; creator enrichment cannot be verified");
        }
        String projection = names.contains("email") ? "employee_id, email" : "employee_id";
        List<Map<String, Object>> rows = flow.dbCustomSteps().schedulerSteps().query(
                "SELECT " + projection + " FROM \"" + schema + "\".\"user\" WHERE id = ?", creatorId);
        if (rows.size() != 1)
            throw new AssertionError("Expected exactly one stand user for id " + creatorId + ", got " + rows.size());
        Map<String, Object> row = rows.get(0);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("employeeId", String.valueOf(row.get("employee_id")));
        Object email = row.get("email");
        if (email != null) expected.put("email", String.valueOf(email));
        return expected;
    }

}
