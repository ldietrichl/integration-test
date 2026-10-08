package util.splittercheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Assertions against the response, independent of the HTTP client and stand lifecycle. */
public final class PrecalcResponseAssertions {
    private PrecalcResponseAssertions() { }

    public static final List<String> COUNTERS = List.of("copiedObjects", "objectsAdded", "objectsDeleted", "notLinkedObjects", "totalObjects", "linkedExps", "totalExps");

    public static long number(JsonNode owner, String field) {
        JsonNode value = owner.path(field);
        assertTrue(value.isIntegralNumber() && value.canConvertToLong(), "Ожидалось целое число long: " + field + "=" + value);
        return value.longValue();
    }
    public static void numberEquals(JsonNode owner, String field, long expected) {
        assertEquals(expected, number(owner, field), field + ": " + owner);
    }
    public static void counterSchema(JsonNode response) {
        assertTrue(response.path("counter").isObject(), "Нет счётчиков предрасчёта: " + response);
        for (String field : COUNTERS) assertTrue(number(response.path("counter"), field) >= 0, "Отрицательный счётчик " + field);
    }
    public static void uuid(JsonNode value) {
        assertTrue(value.isTextual(), "UUID должен быть строкой: " + value);
        UUID id = assertDoesNotThrow(() -> UUID.fromString(value.textValue()));
        assertEquals(id.toString(), value.textValue().toLowerCase(Locale.ROOT), "Неполная запись UUID");
    }
    public static void precalculated(JsonNode request, JsonNode response) {
        assertTrue(response.isObject(), response.toString());
        assertFalse(response.hasNonNull("errorCode"), "Ошибка вместо предрасчёта: " + response);
        uuid(response.path("responseId"));
        counterSchema(response);
        if (request.hasNonNull("soConfigVersion")) numberEquals(response, "soConfigVersion", number(request, "soConfigVersion"));
        else assertTrue(response.path("soConfigVersion").isNull() || response.path("soConfigVersion").isMissingNode(), response.toString());
    }
    public static void splitEnvelope(JsonNode request, JsonNode response, long version) {
        assertEquals(request.path("requestId"), response.path("requestId"));
        assertEquals(request.path("splittingId"), response.path("splittingId"));
        assertFalse(response.hasNonNull("errorCode"), response.toString());
        numberEquals(response, "splittingConfigVersion", version);
        uuid(response.path("responseId"));
        assertTrue(response.path("splittingResults").isArray(), response.toString());
        Set<String> expected = new HashSet<>(), actual = new HashSet<>();
        request.path("splittingObjects").forEach(o -> {
            assertTrue(o.path("objectId").isTextual());
            assertTrue(expected.add(o.path("objectId").textValue()), "Дубли в тестовом запросе");
        });
        response.path("splittingResults").forEach(o -> {
            assertTrue(o.path("objectId").isTextual(), o.toString());
            assertTrue(actual.add(o.path("objectId").textValue()), "Дубли objectId: " + response);
            assertTrue(o.path("objectResults").isArray(), o.toString());
            Set<String> rules = new HashSet<>();
            o.path("objectResults").forEach(rule -> {
                assertTrue(rule.path("ruleCode").isTextual(), rule.toString());
                assertTrue(rules.add(rule.path("ruleCode").textValue()), "Повтор ruleCode: " + o);
                assertTrue(rule.path("resultExps").isArray(), rule.toString());
            });
        });
        // These fixtures enable empty-objects-response-enabled and do not suppress objects.
        assertEquals(expected, actual, "Потеряны или добавлены объекты ответа");
    }

    public static void rejected(int status, JsonNode body, JsonNode request, String endpoint) {
        assertEquals(400, status, "Невалидный предрасчёт должен отклоняться HTTP-адаптером");
        assertTrue(body.isObject(), "Ожидалось тело ошибки: " + body);
        assertFalse(body.has("counter") || body.has("splittingResults"), "Ошибка содержит результат: " + body);
        if (body.has("errorCode")) {
            assertEquals("VALIDATION_FAILED", body.path("errorCode").asText(), body.toString());
            if (request.hasNonNull("requestId") && !request.path("requestId").asText().isEmpty())
                assertEquals(request.get("requestId"), body.get("requestId"), "Потерян requestId");
            // Specification v16 makes errorDetails optional and permits an object, not only a string.
            JsonNode details = body.path("errorDetails");
            if (!details.isMissingNode() && !details.isNull()) {
                String text = details.isTextual() ? details.asText() : details.toString();
                assertFalse(text.contains("NullPointerException") || text.contains("Duplicate key"), body.toString());
            }
        } else {
            // Spring MVC may reject @Valid before the SDK. Its standard envelope does not
            // promise validation details; do not mistake this for SDK cause-chain coverage.
            numberEquals(body, "status", 400);
            assertEquals("Bad Request", body.path("error").asText(), body.toString());
            assertEquals(endpoint, body.path("path").asText(), body.toString());
        }
        assertFalse(body.toString().contains("Duplicate key"), body.toString());
        assertFalse(body.toString().contains("NullPointerException"), body.toString());
    }
}
