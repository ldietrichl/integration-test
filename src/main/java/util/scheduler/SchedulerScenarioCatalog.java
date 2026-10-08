package util.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Allure;
import steps.rest.scheduler.SchedulerSteps;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Stable documentation and Allure identities, independent of test implementation classes. */
public final class SchedulerScenarioCatalog {
    private static final JsonNode PLAN = loadPlan();

    private SchedulerScenarioCatalog() {
    }

    public static String identity(int scenarioId) {
        return String.format(Locale.ROOT, "SCH-%03d", scenarioId);
    }

    public static JsonNode metadata(int scenarioId) {
        JsonNode item = PLAN.path("tests").get(scenarioId - 1);
        if (item == null) {
            throw new IllegalArgumentException("Unknown scheduler scenario: " + scenarioId);
        }
        assertEquals(identity(scenarioId), item.path("id").asText());
        return item;
    }

    public static void attachLabels(int scenarioId, String environment) {
        JsonNode item = metadata(scenarioId);
        String key = identity(scenarioId);
        Allure.label("scenarioId", key);
        Allure.label("requirement", item.path("req").asText());
        Allure.story(item.path("req").asText());
        Allure.description(item.path("steps").asText() + "\nExpected: " + item.path("expected").asText());
        Allure.parameter("Specification", "ExpLab v17 scheduler; see scenario catalog source pages");
        Allure.getLifecycle().updateTestCase(result -> {
            result.setName(key + ". " + item.path("title").asText());
            result.setTestCaseId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
            result.setHistoryId(UUID.nameUUIDFromBytes(
                    (key + ":" + environment).getBytes(StandardCharsets.UTF_8)).toString());
        });
    }

    private static JsonNode loadPlan() {
        try (InputStream input = SchedulerScenarioCatalog.class.getResourceAsStream("/scheduler/scenarios.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing scheduler scenario catalog");
            }
            return SchedulerSteps.JSON.readTree(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load scheduler scenario catalog", exception);
        }
    }
}
