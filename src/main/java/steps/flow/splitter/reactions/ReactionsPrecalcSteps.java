package steps.flow.splitter.reactions;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;
import infrastructure.kubernetes.WorkloadScenarioEvidence;
import steps.reporting.ReportingSteps;

import config.services.splitter.ReactionsPrecalcProfile;
import config.extensions.ReactionsPrecalcBindings;
import config.environment.special.EnvironmentConfigWithReactionsPrecalc;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import flow.Flows;
import config.extensions.WorkloadRunScopeExtension;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.splittercheck.SplitterResponseReader;
import util.support.SplitterVersionProvider;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

/** All fixtures are local to this task; no changes to shared project configuration. */
@config.extensions.WorkloadScenario("EXPLAB-2885")
@ExtendWith({PerfeccionistaExtension.class, ReactionsPrecalcBindings.class,
        WorkloadRunScopeExtension.class})
@SetEnvironmentConfiguration(EnvironmentConfigWithReactionsPrecalc.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("splitter-config")
public abstract class ReactionsPrecalcSteps extends Flows { // spread=6313
    public final String namespace = "2885-" + UUID.randomUUID() + "-";
    public long version;
    public volatile WorkloadScenarioEvidence diagnostics;
    private ReactionsPrecalcProfile managedStand;

    public String managedProfile() { return ""; }
    public void restartManagedStand() { managedStand.restart(); }

    @BeforeEach
    public void writeSplitterRuntimeMetadataToAllure() {
        Allure.addAttachment("Splitter runtime metadata", "text/plain",
                util.support.SplitterRuntimeMetadata.summary("REACTIONS"), ".txt");
    }

    @BeforeEach
    protected void prepareExplab2885StandState() {
        diagnostics = WorkloadScenarioEvidence.current();
        try { managedStand = ReactionsPrecalcProfile.prepare(managedProfile(), diagnostics); }
        catch (RuntimeException | Error failure) {
            if (diagnostics != null) diagnostics.failed(failure);
            throw failure;
        }
    }

    @AfterEach
    protected void collectExplab2885Diagnostics() {
        if (diagnostics != null) diagnostics.finish();
    }

    public String key(String name) { return namespace + name; }
    public void scenario(String title, Consumer<FlowWithRest> action) {
        Allure.description(title);
        // An unnamed flow dispatch adds no wrapper step; operations and assertions are visible directly.
        getFlowWithRest().step(f -> {
            action.accept(f);
            diagnostics.event("FLOW_ASSERTIONS_PASSED");
        }).run();
    }

    public ValidatableResponseWrapper rawLoad(FlowWithRest f, ObjectNode p) {
        return observed("LOAD_CONFIG", p, () -> f.restCustomSteps().splitterSteps().loadReactionsConfig(p));
    }
    public ValidatableResponseWrapper rawCalculate(FlowWithRest f, ObjectNode p) {
        return observed("PRE_CALCULATE", p, () -> f.restCustomSteps().splitterSteps().calculateReactionsPreliminary(p));
    }
    public ValidatableResponseWrapper rawSplit(FlowWithRest f, ObjectNode p) {
        return observed("SPLIT", p, () -> f.restCustomSteps().splitterSteps().splitReactions(p));
    }
    private ValidatableResponseWrapper observed(String operation, ObjectNode request, Supplier<ValidatableResponseWrapper> call) {
        var evidence = diagnostics;
        String id = request.path("requestId").asText("").replaceAll("[^A-Za-z0-9_.-]", "_");
        if (id.length() > 100) id = id.substring(0, 100);
        String label = operation + " requestId=" + id;
        if (evidence != null) evidence.event(label + " START");
        long started = System.nanoTime();
        try {
            var response = call.get();
            var snapshot = SplitterResponseReader.snapshot(response);
            if (evidence != null) evidence.event(label + " RETURN elapsedMs=" + (System.nanoTime() - started) / 1_000_000
                    + " body=" + (snapshot.hasJsonBody() ? "JSON" : snapshot.isEmptyBody() ? "EMPTY" : "UNAVAILABLE_OR_NON_JSON"));
            if (snapshot.isEmptyBody() && "PRE_CALCULATE".equals(operation))
                Allure.addAttachment("EXPLAB-2885 empty pre-calculate response", "text/plain",
                        "An empty acknowledgement does not establish that precalculation is enabled. Check configuration-and-pods, "
                                + "pod-log and monitoring for PRECALC_NOT_ENABLED / Preliminary calculation disabled! "
                                + "The JSON response contract assertion remains active.", ".txt");
            return response;
        } catch (RuntimeException | Error failure) {
            if (evidence != null) evidence.event(label + " THROW " + failure.getClass().getName());
            throw failure;
        }
    }
    public ObjectNode preObject(String id, String value) {
        ObjectNode o = JSON.createObjectNode().put("uniqueConfigurationId", key(id));
        o.putArray("objectParams").add(param("segment", value));
        return o;
    }
    public ObjectNode pre(Integer v, ObjectNode... objects) {
        ObjectNode p = JSON.createObjectNode().put("requestId", UUID.randomUUID().toString());
        if (v != null) p.put("soConfigVersion", v);
        ArrayNode os = p.putArray("splittingObjects");
        Arrays.stream(objects).forEach(os::add);
        return p;
    }
    public ObjectNode obj(String objectId, String uniqueId, String value) {
        ObjectNode o = JSON.createObjectNode().put("objectId", objectId);
        if (uniqueId != null) o.put("uniqueConfigurationId", key(uniqueId));
        o.putArray("objectParams").add(param("segment", value));
        return o;
    }
    public JsonNode body(ValidatableResponseWrapper response) {
        return SplitterResponseReader.snapshot(response).requireJsonBody("EXPLAB-2885: JSON response required");
    }
    public JsonNode ok(ValidatableResponseWrapper response) { return body(response.should(haveStatusCode(200))); }
    public JsonNode load(FlowWithRest f, ObjectNode... experiments) {
        return ReportingSteps.step("Загрузить конфигурацию REACTIONS: экспериментов " + experiments.length, () -> {
            version = SplitterVersionProvider.next();
            JsonNode r = ok(rawLoad(f, config(version, experiments)));
            assertTrue(Set.of("LOADED", "LOADED_WITH_PRECALC").contains(r.path("result").asText()),
                    "Configuration was not loaded. For Manual config load disabled verify api-config-load=true for REST "
                            + "or use Kafka mode; see EXPLAB-2885 diagnostics. Response: " + r);
            assertEquals(version, r.path("currentConfigVersion").asLong(-1), r.toString());
            return r;
        });
    }
    public JsonNode calculate(FlowWithRest f, ObjectNode p) {
        return ReportingSteps.step("Выполнить предрасчёт: объектов " + p.path("splittingObjects").size(), () -> {
            Allure.addAttachment("EXPLAB-2885 pre-calculate request", "application/json", p.toPrettyString(), ".json");
            JsonNode r = ok(rawCalculate(f, p));
            if (p.hasNonNull("soConfigVersion")) assertEquals(p.get("soConfigVersion").asLong(), r.path("soConfigVersion").asLong(-1));
            else assertTrue(r.path("soConfigVersion").isNull() || r.path("soConfigVersion").isMissingNode(), r.toString());
            return r;
        });
    }
    public void seed(FlowWithRest f) {
        load(f, exp(101, 1));
        calculate(f, pre(100)); // isolate a complete object set from previous tests
        calculate(f, pre(101, preObject("U1", "gold"), preObject("U2", "silver")));
    }
    public JsonNode split(FlowWithRest f, ObjectNode req) {
        return ReportingSteps.step("Выполнить split REACTIONS и проверить контракт ответа", () -> {
            Allure.addAttachment("EXPLAB-2885 split request", "application/json", req.toPrettyString(), ".json");
            JsonNode r = ok(rawSplit(f, req));
            assertEquals(req.path("requestId"), r.path("requestId"));
            assertEquals(req.path("splittingId"), r.path("splittingId"));
            assertEquals(version, r.path("splittingConfigVersion").asLong(-1), r.toString());
            assertDoesNotThrow(() -> UUID.fromString(r.path("responseId").asText()));
            assertUnique(r);
            Set<String> requested = new HashSet<>();
            req.path("splittingObjects").forEach(o -> requested.add(o.path("objectId").asText()));
            r.path("splittingResults").forEach(o -> assertTrue(requested.contains(o.path("objectId").asText()), "Unexpected object " + o));
            return r;
        });
    }
    public JsonNode splitOne(FlowWithRest f, String id, String value) {
        return split(f, request(S_B, obj("O1", id, value)));
    }
    public void error(ValidatableResponseWrapper r, String code) {
        ReportingSteps.step("Проверить отказ с кодом " + code, () -> {
            // SDK error code is stable; the host's HTTP mapping is a separate contract.
            JsonNode b = body(r);
            assertEquals(code, b.path("errorCode").asText(), b.toString());
            assertFalse(b.has("splittingResults"), b.toString());
        });
    }
    public void validationRejected(ValidatableResponseWrapper response) {
        ReportingSteps.step("Проверить отклонение невалидного запроса предрасчёта", () -> {
            String layer = assertPrecalcValidationRejection(response.toResponse().statusCode(), body(response));
            Allure.parameter("EXPLAB-2885 validation layer", layer);
        });
    }
}
