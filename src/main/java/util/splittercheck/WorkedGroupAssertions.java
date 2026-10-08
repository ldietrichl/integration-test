package util.splittercheck;

import com.fasterxml.jackson.databind.JsonNode;
import dto.splitter.common.ParamDto;
import dto.splitter.config.ExperimentDto;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ticket-specific oracle, independent of HTTP, Kafka and the framework Environment. */
public final class WorkedGroupAssertions {
    private WorkedGroupAssertions() { }

    /** A full row identity: multiple linked groups of one experiment are legal in the report. */
    public record ExpectedRow(int expId, int conditionId, String expGroup, String finalExpGroup) { }

    /** The flag belongs to a linked row, not just an experiment ID or an entire object. */
    public record AlternativeRow(String objectId, ExpectedRow row) { }

    public static void alternativeFlags(JsonNode body, Map<String, List<ExpectedRow>> expectedAll,
                                        Set<AlternativeRow> marked) {
        objects(body, new ArrayList<>(expectedAll.keySet()));
        for (AlternativeRow expected : marked)
            assertTrue(expectedAll.getOrDefault(expected.objectId(), List.of()).contains(expected.row()),
                    "Marked oracle row must exist in the expected ALL: " + expected);
        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        for (JsonNode object : body.path("splittingResults")) {
            String objectId = object.path("objectId").asText();
            checks.add(() -> rows(object, "ALL", expectedAll.get(objectId)));
            for (JsonNode row : ruleRows(object, "ALL")) checks.add(() -> {
                var identity = new ExpectedRow(row.path("expId").intValue(), row.path("conditionId").intValue(),
                        row.path("expGroup").asText(), row.path("finalExpGroup").isNull() ? null : row.path("finalExpGroup").asText());
                var flags = row.path("expFlags");
                assertEquals(Set.of("isAlternative"), unique(flags, "code"), "MAPPER ALL flag codes: " + row);
                // Existing MAPPER DTO contract serializes flag values as strings. Do not coerce booleans/null.
                assertEquals(Boolean.toString(marked.contains(new AlternativeRow(objectId, identity))),
                        text(find(flags, "code", "isAlternative").path("value")), "Alternative for " + objectId + ": " + identity);
            });
        }
        assertAll("Exact MAPPER alternative flags", checks);
    }

    /** mapperFilter p.304: alternative suppression and MAIN parameter suppression are independent. */
    public static void alternativeFiltering(JsonNode body, Map<String, List<ExpectedRow>> main,
                                            Set<AlternativeRow> marked, dto.splitter.config.LoadConfigRequestDto config,
                                            boolean enabled, boolean filterAlternative) {
        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        for (JsonNode object : body.path("splittingResults")) {
            String id = object.path("objectId").asText();
            if (main.get(id).isEmpty()) continue;
            boolean byAlternative = filterAlternative && marked.stream().anyMatch(m -> m.objectId().equals(id));
            boolean byMain = main.get(id).stream().anyMatch(row -> config.getSplittingConfig().getExperiments().stream()
                    .filter(e -> e.getId() == row.expId()).flatMap(e -> e.getGroups().stream())
                    .filter(g -> g.getCode().equals(row.expGroup())).flatMap(g -> g.getSplittingResults().stream())
                    .filter(r -> r.getConditionId() == row.conditionId()).flatMap(r -> r.getResultParams().stream())
                    .anyMatch(p -> p.getParamCode().equals("actionType") && p.getParamValues().stream().anyMatch(Set.of("2", "4")::contains)));
            checks.add(() -> filteredFlag(object.path("objectFlags"), enabled && (byAlternative || byMain)));
        }
        assertAll("Documented mapperFilter", checks);
    }

    public static JsonNode reportBody(JsonNode envelope, dto.splitter.split.SplitRequestDto request,
                                      String point, long version) {
        assertAll("Report envelope",
                () -> assertEquals("SPLITTER", envelope.path("source").asText()),
                () -> assertEquals(request.getRequestId(), envelope.path("requestId").asText()),
                () -> assertEquals(point, envelope.path("messageInfo").path("splittingPoint").asText()));
        JsonNode body = envelope.path("message");
        assertTrue(body.isObject(), "Report message must be an object");
        assertAll("Report correlation",
                () -> assertEquals(request.getRequestId(), body.path("requestId").asText()),
                () -> assertEquals(request.getSplittingId(), body.path("splittingId").asText()),
                () -> assertIntegral(body.path("splittingConfigVersion"), version, "configVersion"));
        return body;
    }

    /** Exact row multiplicity, without requiring the unresolved no-MAIN container shape. */
    public static void rows(JsonNode object, String rule, List<ExpectedRow> expected) {
        JsonNode actual = ruleRows(object, rule);
        List<ExpectedRow> identities = new ArrayList<>();
        for (JsonNode row : actual) {
            assertTrue(row.path("expId").isIntegralNumber(), "expId integer required");
            assertTrue(row.path("conditionId").isIntegralNumber(), "conditionId integer required");
            assertTrue(row.has("finalExpGroup"), "finalExpGroup required");
            assertTrue(row.path("finalExpGroup").isNull() || row.path("finalExpGroup").isTextual(), "finalExpGroup type");
            identities.add(new ExpectedRow(row.path("expId").intValue(), row.path("conditionId").intValue(),
                    text(row.path("expGroup")), row.path("finalExpGroup").isNull() ? null : text(row.path("finalExpGroup"))));
        }
        assertEquals(expected.size(), identities.size(), rule + " row count for " + object.path("objectId"));
        assertEquals(frequencies(expected), frequencies(identities), rule + " exact rows for " + object.path("objectId"));
    }

    private static Map<ExpectedRow, Integer> frequencies(List<ExpectedRow> rows) {
        Map<ExpectedRow, Integer> counts = new HashMap<>();
        rows.forEach(row -> counts.merge(row, 1, Integer::sum)); return counts;
    }

    public static JsonNode ruleRows(JsonNode object, String rule) {
        JsonNode rules = object.path("objectResults");
        if (empty(rules)) return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        unique(rules, "ruleCode");
        for (JsonNode entry : rules) if (rule.equals(entry.path("ruleCode").asText())) {
            assertTrue(entry.path("resultExps").isArray(), rule + ".resultExps must be an array");
            return entry.path("resultExps");
        }
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    }

    /** R4: even a single false or null-valued isAlternative is forbidden in REACTIONS. */
    public static void candidateFlags(JsonNode body, boolean reactions) {
        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        for (JsonNode object : body.path("splittingResults")) {
            checks.add(() -> { if (!empty(object.path("objectFlags"))) unique(object.path("objectFlags"), "code"); });
            for (JsonNode rule : object.path("objectResults")) for (JsonNode row : rule.path("resultExps")) {
                checks.add(() -> {
                    JsonNode flags = row.path("expFlags");
                    if (empty(flags)) return;
                    Set<String> codes = unique(flags, "code");
                    if (reactions) assertFalse(codes.contains("isAlternative"), "REACTIONS must not emit isAlternative: " + row);
                    if (rule.path("ruleCode").asText().equals("MAIN"))
                        fail("MAIN must not contain experiment flags: " + row);
                });
            }
        }
        assertAll("Flag absence and duplicate codes", checks);
    }

    public static void candidateResult(JsonNode body, dto.splitter.split.SplitRequestDto request,
                                       dto.splitter.config.LoadConfigRequestDto config, int spread,
                                       Map<String, List<ExpectedRow>> expectedMain,
                                       Map<String, List<ExpectedRow>> expectedAll, boolean reactions) {
        objects(body, request.getSplittingObjects().stream().map(dto.splitter.split.SplittingObjectDto::getObjectId).toList());
        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        checks.add(() -> candidateFlags(body, reactions));
        for (JsonNode object : body.path("splittingResults")) {
            String id = object.path("objectId").textValue();
            checks.add(() -> rows(object, "MAIN", Objects.requireNonNull(expectedMain.get(id), "MAIN oracle for " + id)));
            if (expectedAll != null) checks.add(() -> rows(object, "ALL", Objects.requireNonNull(expectedAll.get(id), "ALL oracle for " + id)));
            checks.add(() -> {
                JsonNode rules = object.path("objectResults");
                if (empty(rules)) return;
                assertTrue(Set.of("MAIN", "ALL").containsAll(unique(rules, "ruleCode")), "Unexpected rule");
                for (JsonNode rule : rules) {
                    assertTrue(rule.path("resultExps").isArray(), "resultExps array required");
                    for (JsonNode row : rule.path("resultExps")) {
                        ExperimentDto source = config.getSplittingConfig().getExperiments().stream()
                                .filter(e -> e.getId().longValue() == row.path("expId").asLong()).findFirst()
                                .orElseThrow(() -> new AssertionError("Unknown expId: " + row));
                        experiment(row, source, spread);
                    }
                }
            });
        }
        assertAll("Candidate selection and full row contract", checks);
    }

    public static void objects(JsonNode root, List<String> expected) {
        assertEquals(expected.size(), new HashSet<>(expected).size(), "Fixture has duplicate object IDs");
        assertEquals(new HashSet<>(expected), unique(root.path("splittingResults"), "objectId"), "Exact response object IDs");
    }

    public static void rules(JsonNode object, Set<String> expected) {
        JsonNode rules = object.path("objectResults");
        if (expected.isEmpty() && empty(rules)) return;
        assertEquals(expected, unique(rules, "ruleCode"), "Exact rules for " + object.path("objectId"));
    }

    public static void noMainFlags(JsonNode flags) {
        if (empty(flags)) return;
        assertEquals(Set.of("filtered"), unique(flags, "code"), "no-MAIN flag codes");
        assertEquals("false", text(flags.get(0).path("value")), "no-MAIN filtered flag");
    }

    public static void noMainRestRules(JsonNode object, boolean reactions, boolean allowWithoutMain,
                                       boolean hasExpectedAll) {
        Set<String> expected = !allowWithoutMain ? Set.of()
                : hasExpectedAll ? Set.of("MAIN", "ALL") : Set.of("MAIN");
        assertAll("no-MAIN REST object",
                () -> rules(object, expected),
                () -> {
                    if (!allowWithoutMain) return;
                    JsonNode rows = find(object.path("objectResults"), "ruleCode", "MAIN").path("resultExps");
                    assertTrue(rows.isArray() && rows.isEmpty(), "no-MAIN requires MAIN.resultExps=[]");
                },
                () -> noMainFlags(object.path("objectFlags")));
    }

    public static void requestTime(JsonNode value, long sent, long received) {
        if (value.isMissingNode()) return;
        assertTrue(validRequestTime(value), "requestDt must be long epoch milliseconds when present");
        assertTrue(value.longValue() >= sent - 300_000 && value.longValue() <= received + 300_000,
                "requestDt outside request window (5 min clock skew allowance)");
    }

    public static void resultTime(JsonNode value, long sent, long received) {
        assertTrue(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0,
                "resultDt must be a positive long epoch microseconds value");
        assertTrue(sent <= received, "Invalid request observation window");
        long micros = value.longValue();
        // Convert without discarding sub-millisecond precision or overflowing a long.
        var actual = java.time.Instant.ofEpochSecond(micros / 1_000_000, (micros % 1_000_000) * 1_000);
        var lower = java.time.Instant.ofEpochMilli(sent).minusSeconds(300);
        var upper = java.time.Instant.ofEpochMilli(received).plusSeconds(300).plusNanos(999_000);
        assertTrue(!actual.isBefore(lower) && !actual.isAfter(upper),
                "resultDt must be epoch microseconds within request window (5 min clock skew allowance): " + micros);
    }

    public static void filteredFlag(JsonNode flags, boolean expected) {
        assertEquals(Set.of("filtered"), unique(flags, "code"), "MAPPER with MAIN requires filtered flag");
        assertEquals(Boolean.toString(expected), text(flags.get(0).path("value")), "MAPPER filtered value");
    }

    public static void correlateRequestTimes(JsonNode api, JsonNode report) {
        // Presence and type are validated independently; absence is valid in either transport.
        if (validRequestTime(api) && validRequestTime(report))
            assertEquals(api.longValue(), report.longValue(), "REST/report requestDt correlation");
    }

    private static boolean validRequestTime(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong();
    }

    public static void mainFlags(JsonNode flags) {
        if (empty(flags)) return;
        assertEquals(Set.of("isAlternative"), unique(flags, "code"), "MAIN flag codes");
        assertTrue(flags.get(0).has("value") && flags.get(0).path("value").isNull(),
                "MAIN may only carry isAlternative=null (specification p.27)");
    }

    public static void parameters(JsonNode exp, List<ParamDto> expected) {
        JsonNode actual = exp.path("groupResultParams");
        if (expected == null || expected.isEmpty()) {
            assertTrue(empty(actual), "Expected empty groupResultParams: " + exp);
            return;
        }
        Set<String> codes = new HashSet<>();
        for (ParamDto param : expected) assertTrue(codes.add(param.getParamCode()), "Duplicate fixture parameter");
        assertEquals(codes, unique(actual, "paramCode"), "Exact groupResultParams codes");
        for (ParamDto param : expected) {
            JsonNode row = find(actual, "paramCode", param.getParamCode());
            assertEquals(param.getDataType(), text(row.path("dataType")), "Parameter datatype: " + param.getParamCode());
            JsonNode values = row.path("paramValues");
            assertTrue(values.isArray(), "paramValues array required");
            List<String> strings = new ArrayList<>();
            values.forEach(value -> strings.add(text(value)));
            assertEquals(param.getParamValues(), strings, "All parameter values: " + param.getParamCode());
        }
    }

    public static void experiment(JsonNode actual, ExperimentDto expected, long spread) {
        assertIntegral(actual.path("expId"), expected.getId().longValue(), "expId");
        assertEquals(expected.getSalt(), text(actual.path("salt")), "salt");
        assertIntegral(actual.path("spreadValue"), spread, "spreadValue");
        nullableInteger(actual.path("layerId"), expected.getLayerId(), "layerId");
        nullableInteger(actual.path("layerPriority"), expected.getLayerPriority(), "layerPriority");
        assertTrue(actual.has("finalExpGroup"), "finalExpGroup must be present (nullable)");
        assertTrue(actual.path("finalExpGroup").isNull() || actual.path("finalExpGroup").isTextual(), "finalExpGroup type");
        String groupCode = text(actual.path("expGroup"));
        var group = expected.getGroups().stream().filter(g -> g.getCode().equals(groupCode)).findFirst()
                .orElseThrow(() -> new AssertionError("Unexpected expGroup: " + groupCode));
        assertTrue(actual.path("conditionId").isIntegralNumber(), "conditionId integer required");
        var result = group.getSplittingResults().stream()
                .filter(r -> r.getConditionId().longValue() == actual.path("conditionId").longValue()).findFirst()
                .orElseThrow(() -> new AssertionError("Unexpected conditionId for group " + groupCode));
        parameters(actual, result.getResultParams());
    }

    private static void nullableInteger(JsonNode node, Integer expected, String label) {
        if (expected == null) assertTrue(node.isMissingNode() || node.isNull(), label + " must be absent/null");
        else assertIntegral(node, expected.longValue(), label);
    }

    private static void assertIntegral(JsonNode node, long expected, String label) {
        assertTrue(node.isIntegralNumber(), label + " integer required");
        assertEquals(expected, node.longValue(), label);
    }

    private static String text(JsonNode node) {
        assertTrue(node.isTextual(), "String required: " + node);
        return node.textValue();
    }

    public static boolean empty(JsonNode node) {
        return node.isMissingNode() || node.isNull() || (node.isArray() && node.isEmpty());
    }

    public static Set<String> unique(JsonNode rows, String key) {
        assertTrue(rows.isArray(), "Array required for " + key);
        Set<String> values = new HashSet<>();
        for (JsonNode row : rows) {
            String value = text(row.path(key));
            assertFalse(value.isBlank(), "Blank " + key);
            assertTrue(values.add(value), "Duplicate " + key + ": " + value);
        }
        return values;
    }

    public static JsonNode find(JsonNode rows, String key, String value) {
        for (JsonNode row : rows) if (value.equals(row.path(key).asText(null))) return row;
        throw new AssertionError("Missing " + key + "=" + value);
    }
}
