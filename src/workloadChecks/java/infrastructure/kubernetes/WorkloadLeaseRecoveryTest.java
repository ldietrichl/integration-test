package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class WorkloadLeaseRecoveryTest {
    ObjectNode deployment;
    final ObjectNode scope = WorkloadJson.JSON.createObjectNode().put("namespace", "dev");
    final Map<String, ObjectNode> maps = new LinkedHashMap<>();
    final List<String> names = List.of("service", "rules");
    final AtomicInteger writes = new AtomicInteger();
    WorkloadLeaseRecovery recovery;

    @BeforeEach void setup() throws Exception {
        deployment = (ObjectNode) WorkloadJson.JSON.readTree("""
                {"metadata":{"uid":"dep-uid"},"spec":{"template":{"spec":{"containers":[
                  {"envFrom":[{"configMapRef":{"name":"service"}},{"configMapRef":{"name":"rules"}}]}
                ]}}}}
                """);
        for (String name : names) {
            ObjectNode cm = WorkloadJson.JSON.createObjectNode();
            var meta = cm.putObject("metadata").put("uid", name + "-uid").put("resourceVersion", "1");
            meta.putObject("annotations").put(WorkloadRunLease.KEY, name + "-owner").put("other", "preserved");
            cm.putObject("data").put("SENSITIVE", "not-in-plan");
            maps.put(name, cm);
        }
        recovery = new WorkloadLeaseRecovery(() -> deployment, maps::get, this::patch);
    }

    void patch(String name, String json) {
        try {
            JsonNode ops = WorkloadJson.JSON.readTree(json);
            assertEquals(4, ops.size());
            for (int i = 0; i < 3; i++) {
                JsonNode op = ops.get(i);
                assertEquals("test", op.path("op").asText());
                if (!maps.get(name).at(op.path("path").asText()).equals(op.path("value")))
                    throw new IllegalStateException("JSON Patch test failed");
            }
            assertEquals("remove", ops.get(3).path("op").asText());
            assertEquals("/metadata/annotations/scheduler-regression.explab~1run-owner", ops.get(3).path("path").asText());
            ((ObjectNode) maps.get(name).at("/metadata/annotations")).remove(WorkloadRunLease.KEY);
            ((ObjectNode) maps.get(name).path("metadata")).put("resourceVersion", "2");
            writes.incrementAndGet();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    @Test void inspectDoesNotWriteOrExposeData() {
        var plan = recovery.inspect(scope, names);
        assertEquals(0, writes.get());
        assertFalse(plan.toString().contains("SENSITIVE"));
        assertFalse(plan.toString().contains("not-in-plan"));
        assertEquals(2, plan.path("configMaps").size());
    }

    @Test void onlyReviewedOwnersRemoved() {
        recovery.release(recovery.inspect(scope, names), scope, names, true);
        assertEquals(2, writes.get());
        for (var cm : maps.values()) {
            assertEquals("preserved", cm.at("/metadata/annotations/other").asText());
            assertEquals("not-in-plan", cm.at("/data/SENSITIVE").asText());
        }
    }

    @Test void refusesMissingConfirmation() {
        assertThrows(IllegalStateException.class, () -> recovery.release(recovery.inspect(scope, names), scope, names, false));
        assertEquals(0, writes.get());
    }

    @ParameterizedTest @ValueSource(strings = {"uid", "resourceVersion", "owner", "data", "deployment", "namespace", "newLease"})
    void detectsChangesBeforeAnyWrite(String kind) {
        var plan = recovery.inspect(scope, names);
        var cm = maps.get("service"); // second in sorted order: verifies complete preflight.
        switch (kind) {
            case "uid", "resourceVersion" -> ((ObjectNode) cm.path("metadata")).put(kind, "changed");
            case "owner" -> ((ObjectNode) cm.at("/metadata/annotations")).put(WorkloadRunLease.KEY, "new-owner");
            case "data" -> ((ObjectNode) cm.path("data")).put("SENSITIVE", "changed");
            case "deployment" -> ((ObjectNode) deployment.path("spec")).put("replicas", 2);
            case "namespace" -> scope.put("namespace", "ift");
            case "newLease" -> ((ObjectNode) cm.at("/metadata/annotations")).remove(WorkloadRunLease.KEY);
        }
        assertThrows(IllegalStateException.class, () -> recovery.release(plan, scope, names, true));
        assertEquals(0, writes.get());
    }

    @Test void refusesUnrelatedMap() {
        assertThrows(IllegalStateException.class, () -> recovery.inspect(scope, List.of("unrelated")));
        assertEquals(0, writes.get());
    }

    @Test void noOwnerNeedsNoWrite() {
        maps.values().forEach(cm -> ((ObjectNode) cm.at("/metadata/annotations")).remove(WorkloadRunLease.KEY));
        recovery.release(recovery.inspect(scope, names), scope, names, true);
        assertEquals(0, writes.get());
    }

    @Test void rejectsRaceBetweenReadAndPatch() {
        var plan = recovery.inspect(scope, names);
        var racing = new WorkloadLeaseRecovery(() -> deployment, maps::get, (name, json) -> {
            ((ObjectNode) maps.get(name).path("metadata")).put("resourceVersion", "race");
            patch(name, json);
        });
        assertThrows(IllegalStateException.class, () -> racing.release(plan, scope, names, true));
        assertEquals(0, writes.get());
        assertTrue(maps.get("rules").at("/metadata/annotations").has(WorkloadRunLease.KEY));
    }

    @Test void ambiguousPatchRequiresNewInspectionAndDoesNotTouchRemainingMap() {
        var plan = recovery.inspect(scope, names);
        var ambiguous = new WorkloadLeaseRecovery(() -> deployment, maps::get, (name, json) -> {
            patch(name, json);
            throw new IllegalStateException("transport failure");
        });
        assertThrows(IllegalStateException.class, () -> ambiguous.release(plan, scope, names, true));
        assertEquals(1, writes.get());
        assertThrows(IllegalStateException.class, () -> recovery.release(plan, scope, names, true));
        assertEquals(1, writes.get());
        recovery.release(recovery.inspect(scope, names), scope, names, true);
        assertEquals(2, writes.get());
    }
}
