package steps.rest.experiments.v2;

import config.services.core.StatusChange2972Settings;
import config.services.rest.config_service.CustomRestService;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.matchers.RestMatchers;
import org.awaitility.Awaitility;
import java.time.Duration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.qameta.allure.Allure;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Domain steps backed by Environment-managed Platform V AT RestClient and matchers. */
public final class StatusChange2972Steps implements AutoCloseable {
    public static final ObjectMapper JSON = new ObjectMapper();
    public final StatusChange2972Settings settings;
    public final String runId = UUID.randomUUID().toString();
    public final List<Long> created = new ArrayList<>();
    private final Set<Long> retainedFixtures = new HashSet<>();
    public void retain(long id) { retainedFixtures.add(id); }
    public final int caseId;
    public final String scenarioLabel;
    public final String prefix;
    public StatusChange2972Steps(StatusChange2972Settings settings, int caseId) {
        this(settings, caseId, "TP-" + caseId);
    }
    public StatusChange2972Steps(StatusChange2972Settings settings, int caseId, String scenarioLabel) {
        this.settings = settings; this.caseId = caseId; this.scenarioLabel = Objects.requireNonNull(scenarioLabel);
        this.prefix = "EXPLAB-2972-" + runId;
    }
    public record Reply(int code, JsonNode json, String body, ValidatableResponseWrapper response) { }
    public Reply call(boolean configuration, String method, String path, Object body, String role) throws Exception {
        RestClient client = configuration
                ? Environment.getForCurrentThread().getService(CustomRestService.class).restClient()
                : Environment.getForCurrentThread().getService(RestService.class).restClient();
        ValidatableResponseWrapper wrapped = io.qameta.allure.Allure.step(
                scenarioLabel + " " + method + " " + path,
                () -> client.request(io.restassured.http.Method.valueOf(method), spec -> {
                    if (body != null) {
                        try { spec.body(JSON.writeValueAsString(body)); }
                        catch (Exception error) { throw new IllegalArgumentException("Invalid request JSON", error); }
                    }
                    if (!role.equals("primary")) spec.header("Authorization", "Bearer " + settings.token(role, configuration));
                    return spec;
                }, path));
        var response = wrapped.toResponse();
        String text = response.asString();
        JsonNode json;
        try { json = text.isBlank() ? NullNode.instance : JSON.readTree(text); }
        catch (Exception ignored) { json = TextNode.valueOf(text); }
        // Do not attach request headers: they contain bearer and TLS authentication data.
        record("HTTP", Map.of("at", Instant.now().toString(), "case", caseId, "env", settings.env, "method", method, "path", path,
                "request", body == null ? "<none>" : body, "status", response.statusCode(), "response", json));
        return new Reply(response.statusCode(), json, text, wrapped);
    }
    public Reply get(long id) throws Exception { return call(false, "GET", "/api/v2/experiments/" + id, null, "primary"); }
    public Reply status(long id, String target) throws Exception { return status(id, target, "primary", UUID.randomUUID().toString()); }
    public Reply status(long id, String target, String role, String requestId) throws Exception {
        ObjectNode body = JSON.createObjectNode().put("requestId", requestId).put("expId", id);
        if (target != null) body.put("status", target);
        return call(false, "POST", "/api/v2/experiments/status", body, role);
    }
    public JsonNode ok(Reply r) { r.response.should(RestMatchers.haveStatusCode(200)); return r.json; }
    public void rejected(Reply r) { assertTrue(r.code >= 400 && r.code < 500, "Expected controlled 4xx, got " + r.code + ": " + r.body); }
    public String statusOf(long id) throws Exception { return ok(get(id)).at("/status/code").asText(); }
    public long create(Boolean autoStart, boolean autoStop, long startOffsetSeconds, long endOffsetSeconds) throws Exception {
        return create(autoStart, autoStop, startOffsetSeconds, endOffsetSeconds, "primary");
    }
    public long create(Boolean autoStart, boolean autoStop, long startOffsetSeconds, long endOffsetSeconds, String role) throws Exception {
        JsonNode template = JSON.readTree(Files.readString(settings.fixture()));
        assertTrue(template.isObject(), "Fixture must be a create-experiment JSON object");
        ObjectNode body = (ObjectNode) template.deepCopy(); body.remove("id");
        body.put("name", prefix + "-" + created.size()).put("salt", UUID.randomUUID().toString());
        body.put("startDt", Instant.now().plusSeconds(startOffsetSeconds).toEpochMilli());
        body.put("endDt", Instant.now().plusSeconds(endOffsetSeconds).toEpochMilli());
        if (autoStart == null) body.putNull("autoStart"); else body.put("autoStart", autoStart);
        body.put("autoStop", autoStop);
        var response = ok(call(false, "POST", "/api/v2/experiments", body, role));
        assertTrue(response.path("id").canConvertToLong(), "Create response has no numeric id");
        long id = response.path("id").asLong(); created.add(id);
        assertTrue(response.path("createdBy").isIntegralNumber() && response.path("createdBy").canConvertToLong(),
                "Create response has no integral createdBy");
        settings.observeUser(role, response.path("createdBy").asLong());
        assertEquals("DRAFT", response.at("/status/code").asText(), "Fixture must start in DRAFT");
        assertEquals(5, response.path("version").asInt(), "Stand prerequisite: experiment V2 version=5");
        return id;
    }
    public long draft() throws Exception { return create(false, false, 172800, 259200); }
    public long fixture(String expected) throws Exception {
        String configured = settings.optional(String.format("case.%02d.fixture-id", caseId), null);
        long id;
        if (configured != null && !configured.isBlank()) {
            id = Long.parseLong(configured);
            var entity = ok(get(id));
            assertTrue(entity.path("name").asText().startsWith("EXPLAB-2972-"), "Managed fixture must have an EXPLAB-2972- name");
        } else {
            id = draft();
            if (!expected.equals("DRAFT")) { ok(status(id, "AGREED")); }
            if (Set.of("STARTING", "IN_PROGRESS", "STOPPING", "STOPPED").contains(expected)) ok(status(id, "STARTING"));
            if (Set.of("IN_PROGRESS", "STOPPING", "STOPPED").contains(expected)) awaitStatus(id, "IN_PROGRESS");
            if (Set.of("STOPPING", "STOPPED").contains(expected)) ok(status(id, "STOPPING"));
            if (expected.equals("STOPPED")) awaitStatus(id, "STOPPED");
        }
        assertEquals(expected, statusOf(id), "Managed fixture has an unexpected status"); return id;
    }
    public void awaitStatus(long id, String target) throws Exception {
        awaitAssertion("Automatic status " + target + " for " + id,
                () -> assertEquals(target, statusOf(id)));
    }

    public void awaitAssertion(String description, org.awaitility.core.ThrowingRunnable assertion) {
        try {
            Awaitility.await(description)
                .pollInSameThread().pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(settings.timeout()))
                .untilAsserted(assertion);
        } catch (org.awaitility.core.ConditionTimeoutException timeout) {
            // An unmet business assertion is FAILED in Allure; infrastructure exceptions remain BROKEN.
            fail(description + ": expected result was not observed within " + settings.timeout() + " seconds", timeout);
        }
    }

    public void warning(Reply r, String code) {
        JsonNode warnings = ok(r).path("warnings"); assertTrue(warnings.isArray(), "Warnings missing: " + r.body);
        boolean found = false; for (var p : warnings) if (code.equals(p.path("code").asText())) found = true;
        assertTrue(found, "Missing warning " + code);
    }
    public void record(String title, Object value) throws Exception {
        String json = JSON.writeValueAsString(value);
        Allure.addAttachment(title, "application/json", json, ".json");
        Path dir = Path.of("build", "explab2972-stand", settings.env, runId); Files.createDirectories(dir);
        Files.writeString(dir.resolve("evidence.jsonl"), json + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    @Override public void close() throws Exception {
        List<Object> retained = new ArrayList<>();
        for (long id : created) {
            try {
                JsonNode current = ok(get(id));
                if (!retainedFixtures.contains(id) && current.path("name").asText().startsWith(prefix) && "DRAFT".equals(current.at("/status/code").asText())
                        && "draft".equals(settings.optional("cleanup", "draft"))) {
                    var deleted = call(false, "DELETE", "/api/v2/experiments/" + id, null, "primary");
                    if (deleted.code >= 200 && deleted.code < 300) continue;
                }
                retained.add(Map.of("id", id, "status", current.at("/status/code").asText()));
            } catch (Exception | AssertionError error) { retained.add(Map.of("id", id, "cleanup", error.getClass().getSimpleName())); }
        }
        record("Fixture manifest", Map.of("case", caseId, "runId", runId, "created", created, "retained", retained));
    }
}
