package ru.sber.qa.splitter.extension;

import util.splittercheck.WorkedGroupAssertions;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.function.Executable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.sber.qa.splitter.extension.SplitterDocumentFixtures.*;

/** Compares exact row identities before checking each row independently. */
final class SplitterDocumentOracle {
    private SplitterDocumentOracle() { }

    static void response(JsonNode root, Fixture fixture, boolean report, boolean allowWithoutMain) {
        assertAll(report ? "Kafka objects" : "REST objects",
                () -> WorkedGroupAssertions.objects(root, new ArrayList<>(fixture.objects().keySet())),
                () -> assertAll("Independent objects", fixture.objects().entrySet().stream()
                        .map(entry -> (Executable) () -> object(WorkedGroupAssertions.find(
                                root.path("splittingResults"), "objectId", entry.getKey()), fixture,
                                entry.getValue(), report, allowWithoutMain))));
    }

    static void correlation(JsonNode report, JsonNode api, Fixture fixture, String point, long sent, long received) {
        assertAll("Report correlation and time",
                () -> assertEquals(fixture.request().getRequestId(), report.path("requestId").textValue(), "requestId"),
                () -> assertEquals(fixture.request().getSplittingId(), report.path("splittingId").textValue(), "splittingId"),
                () -> {
                    assertTrue(report.path("splittingConfigVersion").isIntegralNumber(), "config version integer");
                    assertEquals(fixture.config().getConfigVersion().longValue(), report.path("splittingConfigVersion").longValue(), "config version");
                },
                () -> {
                    assertTrue(api.path("responseId").isTextual() && !api.path("responseId").textValue().isBlank(), "REST responseId required");
                    assertEquals(api.path("responseId"), report.path("responseId"), "responseId");
                },
                () -> assertEquals(fixture.reactions() ? "REACTIONS" : "MAPPER", point, "splittingPoint"),
                () -> WorkedGroupAssertions.resultTime(report.path("resultDt"), sent, received),
                () -> WorkedGroupAssertions.requestTime(report.path("requestDt"), sent, received),
                () -> WorkedGroupAssertions.correlateRequestTimes(api.path("requestDt"), report.path("requestDt")));
    }

    private static void object(JsonNode object, Fixture fixture, ObjectResult expected, boolean report, boolean allow) {
        List<Row> all = report ? expected.reportAll() : expected.restAll();
        if (!report && expected.main().isEmpty() && !allow) all = List.of();
        boolean linkedWithoutMain = !report && expected.main().isEmpty() && !expected.reportAll().isEmpty();
        List<Row> expectedAll = all;
        Set<String> rules = new HashSet<>();
        if (!expected.main().isEmpty()) rules.add("MAIN");
        if (!all.isEmpty()) rules.add("ALL");
        assertAll("Object " + object.path("objectId").asText(),
                () -> {
                    if (linkedWithoutMain) WorkedGroupAssertions.noMainRestRules(object, fixture.reactions(), allow, !expectedAll.isEmpty());
                    else WorkedGroupAssertions.rules(object, rules);
                },
                () -> {
                    JsonNode flags = object.path("objectFlags");
                    if (expected.main().isEmpty()) WorkedGroupAssertions.noMainFlags(flags);
                    else if (fixture.reactions()) assertTrue(WorkedGroupAssertions.empty(flags), "REACTIONS object flags");
                    else WorkedGroupAssertions.filteredFlag(flags, expectedFiltered(fixture, expected));
                },
                () -> { if (!expected.main().isEmpty()) rows(object, "MAIN", expected.main(), fixture); },
                () -> { if (!expectedAll.isEmpty()) rows(object, "ALL", expectedAll, fixture); });
    }

    private static void rows(JsonNode object, String code, List<Row> expected, Fixture fixture) {
        JsonNode rows = WorkedGroupAssertions.find(object.path("objectResults"), "ruleCode", code).path("resultExps");
        assertTrue(rows.isArray(), code + ".resultExps must be an array");
        Map<String, List<JsonNode>> actual = new HashMap<>();
        for (JsonNode row : rows) actual.computeIfAbsent(key(row), ignored -> new ArrayList<>()).add(row);
        Set<String> identities = new HashSet<>();
        for (Row row : expected) assertTrue(identities.add(row.key()), "Duplicate fixture row " + row.key());
        assertAll("Exact " + code + " rows",
                () -> assertEquals(expected.size(), rows.size(), "Row count (duplicates forbidden)"),
                () -> assertEquals(identities, actual.keySet(), "Row identities expId/group/condition"),
                () -> assertAll("Independent rows", expected.stream().map(row -> (Executable) () -> {
                    List<JsonNode> matches = actual.getOrDefault(row.key(), List.of());
                    assertEquals(1, matches.size(), "One row for " + row.key());
                    JsonNode value = matches.get(0);
                    assertAll(row.key(),
                            () -> WorkedGroupAssertions.experiment(value, fixture.experiment(row.expId()), fixture.spread()),
                            () -> {
                                assertTrue(value.has("finalExpGroup"), "finalExpGroup required");
                                if (row.finalGroup() == null) assertTrue(value.path("finalExpGroup").isNull(), "finalExpGroup must be null");
                                else assertEquals(row.finalGroup(), value.path("finalExpGroup").textValue(), "finalExpGroup");
                            },
                            () -> flags(value.path("expFlags"), code, row.alternative()));
                })));
    }

    private static String key(JsonNode row) {
        // Keep malformed numeric types distinct instead of coercing strings/floats to valid IDs.
        String exp = row.path("expId").isIntegralNumber() && row.path("expId").canConvertToLong()
                ? Long.toString(row.path("expId").longValue()) : "invalid:" + row.path("expId");
        String condition = row.path("conditionId").isIntegralNumber() && row.path("conditionId").canConvertToInt()
                ? Integer.toString(row.path("conditionId").intValue()) : "invalid:" + row.path("conditionId");
        return exp + "/" + row.path("expGroup").textValue() + "/" + condition;
    }

    private static void flags(JsonNode flags, String rule, boolean alternative) {
        if (rule.equals("MAIN")) { WorkedGroupAssertions.mainFlags(flags); return; }
        if (!alternative && WorkedGroupAssertions.empty(flags)) return;
        assertEquals(Set.of("isAlternative"), WorkedGroupAssertions.unique(flags, "code"), "ALL flag codes");
        assertTrue(flags.get(0).path("value").isTextual(), "ALL flag value is a string");
        assertEquals(Boolean.toString(alternative), flags.get(0).path("value").textValue(), "ALL alternative flag");
    }
}
