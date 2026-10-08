package util.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import steps.db.scheduler.SchedulerDbSteps;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Reusable response and persistence assertions; requests stay visible in the test flows. */
public final class SchedulerAssertions {
    private SchedulerAssertions() {
    }

    public static List<JsonNode> content(JsonNode node) {
        JsonNode array = node.isArray() ? node : node.get("content");
        assertNotNull(array, "content missing");
        assertTrue(array.isArray(), "content must be an array");
        List<JsonNode> result = new ArrayList<>();
        array.forEach(result::add);
        return result;
    }

    public static Set<Long> ids(List<JsonNode> items) {
        return items.stream().map(item -> item.path("id").asLong()).collect(Collectors.toSet());
    }

    /** ExpLab v17 pp.2644-2647 specifies integer registry timestamps; Swagger drift is reported separately. */
    public static void assertDocumentedEpochInteger(JsonNode value, String location) {
        assertTrue(value.isIntegralNumber() && value.canConvertToLong(),
                location + " must be an integer epoch timestamp (ExpLab v17 pp.2644-2647), got "
                        + value.getNodeType());
    }

    /** Ordering is independent of the wire-type assertion. */
    public static int compareRegistryTime(JsonNode left, JsonNode right, String field) {
        var mapping = new steps.rest.scheduler.SchedulerRegistrySteps.Field(
                field, "/" + field, steps.rest.scheduler.SchedulerRegistrySteps.Kind.TIME, false, true);
        return mapping.compare(mapping.value(left), mapping.value(right));
    }

    public static long number(Object value) {
        assertInstanceOf(Number.class, value);
        return ((Number) value).longValue();
    }

    public static long id(JsonNode body) {
        assertTrue(body.path("id").isIntegralNumber(), "id must be an integer");
        return body.path("id").asLong();
    }

    public static void shouldHaveOwnedRowCount(SchedulerDbSteps db, String owner, int count) {
        assertEquals(count, db.tasks(owner).size(), "Unexpected fixture mutation");
    }

    public static void shouldHaveVersions(SchedulerDbSteps db, List<JsonNode> rows, Integer version) {
        for (JsonNode row : rows) {
            assertEquals(version, db.task(id(row)).get("version"));
        }
    }

    public static void shouldMatchPersistedStatuses(SchedulerDbSteps db, JsonNode observation) {
        if (observation.at("/phases/after/tasks").isArray()) {
            for (JsonNode task : observation.at("/phases/after/tasks")) {
                assertEquals(task.path("status").asText(), db.task(task.path("id").asLong()).get("status"),
                        "Prepared snapshot differs from actual DB");
            }
        }
    }
}
