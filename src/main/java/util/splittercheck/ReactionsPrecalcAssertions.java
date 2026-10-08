package util.splittercheck;

import steps.reporting.ReportingSteps;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

import static dto.splitter.precalc.ReactionsPrecalcRequests.*;

/** Reactions preliminary calculation response contract checks. */
public final class ReactionsPrecalcAssertions {
    private ReactionsPrecalcAssertions() { }

    public static void assertUnique(JsonNode r) {
        assertTrue(r.path("splittingResults").isArray(), r.toString());
        Set<String> ids = new HashSet<>();
        r.path("splittingResults").forEach(o -> {
            assertTrue(o.path("objectId").isTextual(), o.toString());
            assertTrue(ids.add(o.path("objectId").asText()), "Duplicate objectId: " + r);
        });
    }
    public static JsonNode object(JsonNode r, String id) {
        assertUnique(r);
        for (JsonNode o : r.path("splittingResults")) if (id.equals(o.path("objectId").asText())) return o;
        return null;
    }
    public static JsonNode rule(JsonNode o, String code) {
        if (o == null) return null;
        JsonNode found = null;
        assertTrue(o.path("objectResults").isArray(), o.toString());
        for (JsonNode r : o.path("objectResults")) if (code.equals(r.path("ruleCode").asText())) {
            assertNull(found, "Duplicate rule " + code); found = r;
            assertTrue(r.path("resultExps").isArray(), r.toString());
        }
        return found;
    }
    public static void assertMain(JsonNode r, String id, int expId, String group, int condition, String marker) {
        assertMain(r, id, expId, group, condition, marker, expId);
    }
    public static void assertMain(JsonNode r, String id, int expId, String group, int condition, String marker, int layerId) {
        ReportingSteps.step("Проверить MAIN: объект=" + id + ", эксперимент=" + expId + ", группа=" + group, () -> {
            JsonNode m = rule(object(r, id), "MAIN");
            assertNotNull(m, "MAIN missing: " + r);
            assertEquals(1, m.path("resultExps").size(), r.toString());
            JsonNode e = m.path("resultExps").get(0);
            assertEquals(expId, e.path("expId").asInt(-1), e.toString());
            assertEquals(group, e.path("expGroup").asText());
            assertEquals(group, e.path("finalExpGroup").asText());
            assertEquals(condition, e.path("conditionId").asInt(-1));
            assertEquals(SALT, e.path("salt").asText());
            assertEquals(layerId, e.path("layerId").asInt(-1));
            List<String> markers = new ArrayList<>();
            e.path("groupResultParams").forEach(p -> { if ("marker".equals(p.path("paramCode").asText())) p.path("paramValues").forEach(v -> markers.add(v.asText())); });
            assertEquals(List.of(marker), markers, e.toString());
        });
    }
    public static void assertMainA(JsonNode r, int expId) { assertMain(r, "O1", expId, "A", 10, expId + "-A-10"); }
    public static void assertAllIds(JsonNode r, Integer... expected) {
        ReportingSteps.step("Проверить ALL: эксперименты " + Arrays.toString(expected), () -> {
            JsonNode all = rule(object(r, "O1"), "ALL"); assertNotNull(all, r.toString());
            List<Integer> actual = new ArrayList<>();
            all.path("resultExps").forEach(e -> actual.add(e.path("expId").asInt(-1)));
            assertEquals(expected.length, actual.size(), "Lost or duplicated linked experiments: " + r);
            assertEquals(new HashSet<>(Arrays.asList(expected)), new HashSet<>(actual));
        });
    }
    public static void assertNoResult(JsonNode r, String id) {
        ReportingSteps.step("Проверить отсутствие результата для объекта " + id, () -> {
            JsonNode o = object(r, id);
            if (o != null) {
                assertTrue(o.path("objectResults").isArray(), r.toString());
                for (JsonNode rule : o.path("objectResults")) {
                    assertTrue(rule.path("resultExps").isArray() && rule.path("resultExps").isEmpty(), r.toString());
                }
            }
        });
    }
    public static void assertCounters(JsonNode response, int copied, int added, int deleted, int unlinked, int total, int linked, int exps) {
        ReportingSteps.step("Проверить счётчики предрасчёта: всего объектов=" + total + ", добавлено=" + added + ", перенесено=" + copied + ", удалено=" + deleted, () -> {
            JsonNode c = response.path("counter");
            String[] names = {"copiedObjects", "objectsAdded", "objectsDeleted", "notLinkedObjects", "totalObjects", "linkedExps", "totalExps"};
            int[] values = {copied, added, deleted, unlinked, total, linked, exps};
            for (int i = 0; i < names.length; i++) {
                assertTrue(c.path(names[i]).isIntegralNumber(), "Missing counter " + names[i] + ": " + response);
                assertEquals(values[i], c.path(names[i]).asInt(), names[i] + ": " + response);
            }
        });
    }
    public static JsonNode canonical(JsonNode node) {
        if (node.isArray()) {
            List<JsonNode> values = new ArrayList<>(); node.forEach(n -> values.add(canonical(n)));
            values.sort(Comparator.comparing(JsonNode::toString));
            ArrayNode result = JSON.createArrayNode(); values.forEach(result::add); return result;
        }
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            TreeSet<String> names = new TreeSet<>(); node.fieldNames().forEachRemaining(names::add);
            names.forEach(n -> result.set(n, canonical(node.get(n)))); return result;
        }
        return node;
    }
    public static void assertEquivalent(JsonNode a, JsonNode b) {
        ReportingSteps.step("Сопоставить результаты обычного расчёта и предрасчёта", () -> {
            assertUnique(a); assertUnique(b);
            assertEquals(canonical(a.path("splittingResults")), canonical(b.path("splittingResults")));
        });
    }
    public static String assertPrecalcValidationRejection(int status, JsonNode body) {
        assertEquals(400, status, "Invalid pre-calculate must be rejected by HTTP adapter");
        assertFalse(body.has("counter") || body.has("splittingResults"), body.toString());
        if (body.has("errorCode")) {
            assertEquals("VALIDATION_FAILED", body.path("errorCode").asText(), body.toString());
            return "SDK_VALIDATION";
        }
        // Bean Validation rejects the DTO before the SDK and uses the host's HTTP error envelope.
        assertEquals(400, body.path("status").asInt(-1), body.toString());
        assertEquals("Bad Request", body.path("error").asText(), body.toString());
        assertEquals("/api/v1/splitter/reactions/pre-calculate", body.path("path").asText(), body.toString());
        return "REST_BEAN_VALIDATION";
    }
}
