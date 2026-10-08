package steps.flow.splitter.mapper;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;
import infrastructure.kubernetes.WorkloadScenarioEvidence;
import steps.reporting.ReportingSteps;

import config.services.splitter.MapperPrecalcProfile;
import config.extensions.MapperPrecalcBindings;
import config.environment.special.EnvironmentConfigWithMapperPrecalc;
import util.splittercheck.PrecalcResponseAssertions;

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
@config.extensions.WorkloadScenario("EXLAB-2891")
@ExtendWith({PerfeccionistaExtension.class, MapperPrecalcBindings.class,
        WorkloadRunScopeExtension.class})
@SetEnvironmentConfiguration(EnvironmentConfigWithMapperPrecalc.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("splitter-config")
public abstract class MapperPrecalcSteps extends Flows { // deterministic ID; the fixture group covers the full range

    public final String namespace = "2891-" + UUID.randomUUID() + "-";
    public long version;
    public volatile WorkloadScenarioEvidence diagnostics;
    private MapperPrecalcProfile managedStand;

    public String managedProfile() { return ""; }
    public void restartManagedStand() { managedStand.restart(); }

    @BeforeEach
    protected void writeSplitterRuntimeMetadataToAllure() {
        Allure.addAttachment("Splitter runtime metadata", "text/plain",
                util.support.SplitterRuntimeMetadata.summary("MAPPER"), ".txt");
    }

    @BeforeEach
    protected void prepareExlab2891StandState() {
        diagnostics = WorkloadScenarioEvidence.current();
        try { managedStand = MapperPrecalcProfile.prepare(managedProfile(), diagnostics); }
        catch (RuntimeException | Error failure) {
            if (diagnostics != null) diagnostics.failed(failure);
            throw failure;
        }
    }

    @AfterEach
    protected void collectExlab2891Diagnostics() {
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
        return observed("LOAD_CONFIG", p, () -> f.restCustomSteps().splitterSteps().loadConfig(p));
    }
    public ValidatableResponseWrapper rawCalculate(FlowWithRest f, ObjectNode p) {
        return observed("PRE_CALCULATE", p, () -> f.restCustomSteps().splitterSteps().calculatePreliminary(p));
    }
    public ValidatableResponseWrapper rawSplit(FlowWithRest f, ObjectNode p) {
        return observed("SPLIT", p, () -> f.restCustomSteps().splitterSteps().split(p));
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
                Allure.addAttachment("EXLAB-2891 empty pre-calculate response", "text/plain",
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
        return SplitterResponseReader.snapshot(response).requireJsonBody("EXLAB-2891: JSON response required");
    }
    public JsonNode ok(ValidatableResponseWrapper response) { return body(response.should(haveStatusCode(200))); }
    public JsonNode load(FlowWithRest f, ObjectNode... experiments) {
        return ReportingSteps.step("Загрузить конфигурацию MAPPER: экспериментов " + experiments.length, () -> {
            version = SplitterVersionProvider.next();
            JsonNode r = ok(rawLoad(f, config(version, experiments)));
            assertTrue(Set.of("LOADED", "LOADED_WITH_PRECALC").contains(r.path("result").asText()),
                    "Configuration was not loaded. For Manual config load disabled verify api-config-load=true for REST "
                            + "or use Kafka mode; see EXLAB-2891 diagnostics. Response: " + r);
            PrecalcResponseAssertions.numberEquals(r, "currentConfigVersion", version);
            return r;
        });
    }
    public JsonNode calculate(FlowWithRest f, ObjectNode p) {
        return ReportingSteps.step("Выполнить предрасчёт: объектов " + p.path("splittingObjects").size(), () -> {
            Allure.addAttachment("EXLAB-2891 pre-calculate request", "application/json", p.toPrettyString(), ".json");
            JsonNode r = ok(rawCalculate(f, p));
            PrecalcResponseAssertions.precalculated(p, r);
            return r;
        });
    }
    public void seed(FlowWithRest f) {
        load(f, exp(101, 1));
        calculate(f, pre(100)); // isolate a complete object set from previous tests
        assertCounters(calculate(f, pre(101, preObject("U1", "gold"), preObject("U2", "silver"))), 0, 2, 0, 1, 2, 1, 1);
        ReportingSteps.step("Подтвердить контрольные связи до невалидного запроса", () -> {
            assertMainA(splitOne(f, "U1", "silver"), 101);
            assertNoResult(splitOne(f, "U2", "gold"), "O1");
        });
    }
    public JsonNode split(FlowWithRest f, ObjectNode req) {
        return ReportingSteps.step("Выполнить split MAPPER и проверить контракт ответа", () -> {
            Allure.addAttachment("EXLAB-2891 split request", "application/json", req.toPrettyString(), ".json");
            JsonNode r = ok(rawSplit(f, req));
            PrecalcResponseAssertions.splitEnvelope(req, r, version);
            return r;
        });
    }
    public JsonNode splitOne(FlowWithRest f, String id, String value) {
        return split(f, request(S_B, obj("O1", id, value)));
    }
}
