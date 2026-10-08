package util.splittercheck;

import steps.reporting.ReportingSteps;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

import static dto.splitter.precalc.MapperPrecalcRequests.*;

/** Mapper preliminary calculation response contract checks. */
public final class MapperPrecalcAssertions {
    private MapperPrecalcAssertions() { }

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
        ReportingSteps.step("Проверить MAIN и ALL: объект=" + id + ", эксперимент=" + expId + ", группа=" + group, () -> {
          for (String code : List.of("MAIN", "ALL")) {
            JsonNode m = rule(object(r, id), code);
            assertNotNull(m, code + " missing: " + r);
            assertEquals(1, m.path("resultExps").size(), r.toString());
            JsonNode e = m.path("resultExps").get(0);
            PrecalcResponseAssertions.numberEquals(e, "expId", expId);
            assertEquals(group, e.path("expGroup").asText());
            assertEquals(group, e.path("finalExpGroup").asText());
            PrecalcResponseAssertions.numberEquals(e, "conditionId", condition);
            assertEquals(SALT, e.path("salt").asText());
            PrecalcResponseAssertions.numberEquals(e, "layerId", expId);
            assertTrue(e.path("groupResultParams").isArray(), e.toString());
            Map<String, JsonNode> params = new HashMap<>();
            e.path("groupResultParams").forEach(p -> {
                assertTrue(p.path("paramCode").isTextual(), p.toString());
                assertNull(params.put(p.path("paramCode").textValue(), p), "Повтор paramCode в результате: " + e);
            });
            assertEquals(Set.of("marker", "actionType"), params.keySet(), e.toString());
            for (String paramCode : List.of("marker", "actionType")) {
                JsonNode p = params.get(paramCode);
                assertEquals(paramCode.equals("marker") ? "STRING" : "INTEGER", p.path("dataType").asText());
                assertEquals(JSON.createArrayNode().add(paramCode.equals("marker") ? marker : "0"), p.path("paramValues"), e.toString());
            }
          }
        });
    }
    public static void assertMainA(JsonNode r, int expId) { assertMain(r, "O1", expId, "A", 10, expId + "-A-10"); }
    public static void assertNoResult(JsonNode r, String id) {
        ReportingSteps.step("Проверить отсутствие результата для объекта " + id, () -> {
            JsonNode o = object(r, id);
            assertNotNull(o, "empty-objects-response-enabled=true: объект должен присутствовать: " + r);
            assertTrue(o.path("objectResults").isArray() && o.path("objectResults").isEmpty(), r.toString());
        });
    }
    public static void assertCounters(JsonNode response, int copied, int added, int deleted, int unlinked, int total, int linked, int exps) {
        ReportingSteps.step("Проверить счётчики предрасчёта: всего объектов=" + total + ", добавлено=" + added + ", перенесено=" + copied + ", удалено=" + deleted, () -> {
            JsonNode c = response.path("counter");
            String[] names = {"copiedObjects", "objectsAdded", "objectsDeleted", "notLinkedObjects", "totalObjects", "linkedExps", "totalExps"};
            int[] values = {copied, added, deleted, unlinked, total, linked, exps};
            for (int i = 0; i < names.length; i++) {
                assertTrue(c.path(names[i]).isIntegralNumber(), "Missing counter " + names[i] + ": " + response);
                PrecalcResponseAssertions.numberEquals(c, names[i], values[i]);
            }
        });
    }
}
