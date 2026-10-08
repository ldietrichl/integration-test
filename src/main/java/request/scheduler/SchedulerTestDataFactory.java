package request.scheduler;

import config.services.core.SchedulerSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request data only. REST and database operations belong to the project flow steps. */
public final class SchedulerTestDataFactory {
    public final String owner = "SCHIT" + UUID.randomUUID().toString().replace("-", "");
    public final long objectId = 100000000000L + (UUID.randomUUID().getMostSignificantBits() & 0x3fffffffffL);
    public final long now = System.currentTimeMillis();
    public final long future = now + 7 * 86400000L;
    private final SchedulerSettings settings;
    private int sequence;

    public SchedulerTestDataFactory(SchedulerSettings settings) {
        this.settings = settings;
    }

    public String nextFixtureName() {
        return owner + "F" + (++sequence);
    }

    public Map<String, Object> action(String type, long object, String action) {
        return new LinkedHashMap<>(Map.of(
                "objectType", type,
                "objectId", object,
                "objectName", owner + "API" + (++sequence),
                "action", action));
    }

    public Map<String, Object> createTaskV2() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scheduleDateTime", future);
        body.put("createdBy", settings.user());
        body.put("splittingPointCode", settings.splittingPoint());
        body.put("actions", new ArrayList<>(List.of(action("EXP", objectId, "START"))));
        return body;
    }

    public Map<String, Object> createTaskV1() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("planExecutionDatetime", future);
        body.put("createdBy", settings.user());
        body.put("action", action("EXP", objectId, "START"));
        return body;
    }

    public Map<String, Object> registry() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", 0);
        body.put("size", 100);
        body.put("filters", List.of(List.of(filter("objectId", "equal", Long.toString(objectId)))));
        body.put("sorts", List.of());
        return body;
    }

    public Map<String, Object> history() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", 0);
        body.put("size", 100);
        body.put("filters", List.of(Map.of(
                "code", "objectId", "operator", "equal", "values", List.of(Long.toString(objectId)))));
        body.put("sorts", List.of());
        return body;
    }

    public static Map<String, Object> filter(String field, String operator, String... values) {
        return new LinkedHashMap<>(Map.of(
                "code", field, "operator", operator, "values", Arrays.asList(values)));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> firstAction(Map<String, Object> request) {
        return ((List<Map<String, Object>>) request.get("actions")).get(0);
    }
}
