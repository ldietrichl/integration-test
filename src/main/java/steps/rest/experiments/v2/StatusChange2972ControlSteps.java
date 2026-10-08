package steps.rest.experiments.v2;

import com.fasterxml.jackson.databind.JsonNode;
import config.services.core.StatusChange2972Settings;
import config.services.rest.StatusChange2972ControlService;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.matchers.RestMatchers;
import java.util.*;

/** Controller protocol and proof-bearing observations; never substitutes a PASS supplied by the controller. */
public final class StatusChange2972ControlSteps implements AutoCloseable {
    public static final Set<Integer> CASES = Set.of(6,9,12,17,30,35,39,40,41,42,43,44,45,54,55,60);
    private final StatusChange2972Steps owner;
    private String session;
    public StatusChange2972ControlSteps(StatusChange2972Steps owner) { this.owner = owner; }
    public JsonNode capabilities() throws Exception { return request("GET", "/capabilities", null); }

    private static JsonNode request(String method, String path, Object body) throws Exception {
        var client = Environment.getForCurrentThread().getService(StatusChange2972ControlService.class).restClient();
        var response = client.request(io.restassured.http.Method.valueOf(method), spec -> {
            if (body != null) {
                try { spec.body(StatusChange2972Steps.JSON.writeValueAsString(body)); }
                catch (Exception error) { throw new IllegalArgumentException(error); }
            }
            return spec;
        }, path);
        response.should(RestMatchers.haveStatusCode(200));
        return StatusChange2972Steps.JSON.readTree(response.toResponse().asString());
    }

    public static void preflight(StatusChange2972Settings settings, int id) throws Exception {
        settings.required("control.base-uri");
        JsonNode capabilities = request("GET", "/capabilities", null);
        if (!settings.env.equals(capabilities.path("environment").asText()))
            throw new IllegalStateException("Controller environment differs from test.properties");
        List<String> required = new ArrayList<>(List.of("sessions", "observations"));
        if (id == 9) required.add("clock");
        if (id == 60) settings.required("case.60.legacy-fixture-id");
        if (id == 17) { required.add("read-barrier"); settings.user("approver"); settings.token("approver", false); }
        if (Set.of(39,40,41,42,43,44,45,60).contains(id)) {
            required.add("jobs"); settings.required("kafka.topics");
        }
        if (id == 43) required.add("enhance-faults");
        if (id == 44) required.add("delivery-faults");
        if (id == 55) required.add("scheduler-faults");
        for (String name : required) if (!capabilities.path("capabilities").path(name).asBoolean())
            throw new IllegalStateException("Controller lacks required capability: " + name + " for TP-" + id);
        io.qameta.allure.Allure.addAttachment("Подтверждённые возможности стенда", "application/json", capabilities.toString(), ".json");
    }

    public void open() throws Exception {
        session = owner.runId;
        command("open", Map.of("case", owner.caseId, "runId", owner.runId, "environment", owner.settings.env));
    }
    public JsonNode command(String action, Object parameters) throws Exception {
        JsonNode result = request("POST", "/sessions/" + session + "/" + action, parameters);
        owner.record("Control " + action, result);
        return result;
    }
    public JsonNode observe() throws Exception {
        JsonNode result = request("GET", "/sessions/" + session + "/observations", null);
        owner.record("Scheduler / audit / monitoring observations", result);
        if (!result.path("complete").asBoolean()) throw new IllegalStateException("Incomplete observation snapshot");
        return result;
    }
    public void jobs(String mode) throws Exception { command("jobs", Map.of("mode", mode)); }
    public void fault(String mode) throws Exception { command("fault", Map.of("mode", mode)); }
    @Override public void close() throws Exception {
        if (session != null) {
            JsonNode result = command("close", Map.of());
            if (!result.path("restored").asBoolean()) throw new IllegalStateException("Controller failed to restore session " + session);
            session = null;
        }
    }
}
