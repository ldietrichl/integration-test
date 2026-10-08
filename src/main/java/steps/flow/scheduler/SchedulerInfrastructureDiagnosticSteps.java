package steps.flow.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.special.EnvironmentConfigWithSchedulerInfrastructure;
import config.services.core.TestEnvironment;
import flow.SchedulerInfrastructureFlow;
import infrastructure.kubernetes.KubernetesTunnelService;
import infrastructure.kubernetes.TunnelSession;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.flow.FlowRunner;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.db.DatabaseService;
import ru.sber.qa.services.rest.RestService;
import steps.container.KubernetesTunnelSteps;
import steps.rest.scheduler.SchedulerSteps;
import steps.rest.scheduler.SchedulerRegistrySteps;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static steps.rest.scheduler.SchedulerSteps.expect;


/** Source-backed reusable flow steps; no JUnit scenario discovery here. */
public final class SchedulerInfrastructureDiagnosticSteps {
    private static final String HEALTH = "/actuator/health";
    private static final String FILTERS = "/api/v1/schedule/dictionary/filters";
    private static final java.util.concurrent.atomic.AtomicReference<String> MTLS_INGRESS_BLOCKER =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final long MTLS_STABILIZATION_SECONDS = 120;

    // Metadata is applied to the actual TestResult by RequiredAllureLabelsExtension.beforeTestWrite.
    // Do not update TestResult metadata from a before/after fixture context.





    public void inf001() {
        MTLS_INGRESS_BLOCKER.set(null);
        run().step("Inspect configuration through the framework ConfigurationService", flow -> {
            var settings = flow.infrastructureSteps().settings();
            KubernetesTunnelSteps.evidence("Configuration without credentials", settings.description());
            assertEquals(TestEnvironment.current(), settings.environment);
            assertTrue(Set.of("dev", "ift", "lt").contains(settings.environment));
            assertEquals("fabric8", settings.transport,
                    "Kubernetes management and internal actuator tunnels must use Fabric8");
            try {
                assertTrue(settings.tlsVerificationEnabled(), "Cluster TLS verification must not be disabled");
                config.services.container.KubernetesCaTrust.requireNativeTrust(settings.nativeClientConfiguration());
            } finally {
                flow.infrastructureSteps().attachTlsDiagnostics();
            }
            assertNotNull(Environment.getForCurrentThread().getService(RestService.class));
            assertNotNull(Environment.getForCurrentThread().getService(DatabaseService.class));
        }).run();
    }

    public void inf002() {
        run().step("Read only the selected service and matching pods", flow -> {
            var target = flow.infrastructureSteps().target();
            assertEquals(flow.infrastructureSteps().settings().namespace, target.namespace());
            assertEquals(flow.infrastructureSteps().settings().service, target.service());
            assertEquals(flow.infrastructureSteps().settings().servicePort, target.servicePort());
            assertTrue(target.targetPort() > 0 && target.targetPort() <= 65535);
            assertFalse(target.pod().isBlank());
            assertTrue(flow.infrastructureSteps().reusesFrameworkClients(),
                    "The framework bridge must return the same owned client list, not allocate a second list");
        }).run();
    }

    public void inf003() {
        run().step("Open Fabric8 tunnel and read application health", flow -> {
            try (TunnelSession session = flow.infrastructureSteps().openNative()) {
                assertTrue(session.isAlive());
                JsonNode health = expect(flow.schedulerSteps(session).call("GET", HEALTH, null), 200);
                assertEquals("UP", health.path("status").asText());
                assertEquals("UP", health.path("components").path("db").path("status").asText(),
                        "Service database health is separate from the framework DB connectivity hypothesis");
            }
        }).run();
    }

    public void inf004() {
        run().step("Inspect the live dictionary contract through strict mTLS ingress", flow -> {
                awaitReadyMtlsIngress(flow);
                JsonNode dictionary = expect(flow.restCustomSteps().schedulerSteps().call("GET", FILTERS, null), 200);
                assertTrue(dictionary.isArray() && !dictionary.isEmpty(), "Dictionary must be a non-empty array");
                List<Map<String, Object>> evidence = new ArrayList<>();
                for (JsonNode field : dictionary) {
                    assertTrue(field.path("code").isTextual() && !field.path("code").asText().isBlank());
                    assertTrue(field.path("filterFlag").isBoolean());
                    assertTrue(field.path("orderFlag").isBoolean());
                    assertTrue(field.path("validOperators").isArray());
                    List<String> operators = new ArrayList<>();
                    for (JsonNode operator : field.path("validOperators")) {
                        assertTrue(operator.isObject(), "validOperators must contain objects, not plain strings");
                        assertTrue(operator.path("code").isTextual() && !operator.path("code").asText().isBlank());
                        assertTrue(operator.path("isMultiple").isBoolean());
                        operators.add(operator.path("code").asText());
                    }
                    evidence.add(Map.of("code", field.path("code").asText(),
                            "filterFlag", field.path("filterFlag").asBoolean(),
                            "orderFlag", field.path("orderFlag").asBoolean(), "operators", operators));
                }
                KubernetesTunnelSteps.evidence("Live dictionary capabilities", evidence);
        }).run();
    }

    public void inf005() {
        run().step("Execute one bounded read-only registry query through strict mTLS ingress", flow -> {
                awaitReadyMtlsIngress(flow);
                JsonNode registry = expect(flow.restCustomSteps().schedulerSteps().registry(Map.of("page", 0, "size", 1)), 200);
                assertTrue(registry.path("content").isArray());
                assertTrue(registry.path("content").size() <= 1, "Requested page size is one");
                assertTrue(registry.path("totalPages").isIntegralNumber());
                assertTrue(registry.path("totalPages").asLong() >= 0);
                KubernetesTunnelSteps.evidence("Registry JSON shapes, without task values", shapes(registry));
        }).run();
    }

    public void inf006() {
        run().step("Select a mapped V2 equal filter through strict mTLS ingress", flow -> {
                awaitReadyMtlsIngress(flow);
                SchedulerRegistrySteps registry = flow.restCustomSteps().schedulerRegistrySteps();
                var fields = registry.metadata();
                var baseline = registry.baseline();
                assumeTrue(!baseline.isEmpty(), "No existing V2 rows; no fixture is created by this diagnostic");
                var selected = registry.chooseFilter(fields, baseline);
                assumeTrue(selected.isPresent(),
                        "V2 metadata/data has no supported non-null equal-filter oracle; see capability attachments");
                var choice = selected.orElseThrow();
                var filtered = registry.filtered(choice);
                assertFalse(filtered.isEmpty(),
                        "The observed value returned no filtered rows; inspect filtering and concurrent stand changes");
                assertTrue(filtered.size() <= SchedulerRegistrySteps.PAGE_SIZE);
                for (JsonNode row : filtered) {
                    JsonNode actual = choice.field().value(row);
                    assertFalse(actual.isNull(), "Every filtered row must expose the selected scalar");
                    assertEquals(0, choice.field().compare(choice.value(), actual),
                            "Every row must satisfy the V2 dictionary's advertised equal operator");
                }
                KubernetesTunnelSteps.evidence("V2 equal-filter outcome", Map.of(
                        "formCode", SchedulerRegistrySteps.FORM_CODE, "paramCode", choice.field().code(),
                        "operator", "equal", "matchedRows", filtered.size(),
                        "note", "No V1 capability assumption, taskNumber constant or unique-id substitution"));
        }).run();
    }

    public void inf007() {
        run().step("Observe date types through strict mTLS ingress; never fabricate a task", flow -> {
                awaitReadyMtlsIngress(flow);
                JsonNode registry = expect(flow.restCustomSteps().schedulerSteps().registry(Map.of("page", 0, "size", 1)), 200);
                assertTrue(registry.path("content").isArray());
                assumeTrue(!registry.path("content").isEmpty(),
                        "No existing task: date serialization hypothesis remains unconfirmed");
                JsonNode row = registry.path("content").get(0);
                KubernetesTunnelSteps.evidence("Observed task field types", shapes(row));
                for (String field : List.of("planDt", "createdAt", "updatedAt")) {
                    JsonNode value = row.path(field);
                    util.scheduler.SchedulerAssertions.assertDocumentedEpochInteger(value, field);
                }
        }).run();
    }

    public void inf008() {
        run().step("Read metrics without introducing a separate HTTP client", flow -> {
            try (TunnelSession session = flow.infrastructureSteps().openNative()) {
                var response = flow.schedulerSteps(session).getText("/actuator/prometheus");
                response.should(RestMatchers.haveStatusCode(200));
                String body = response.toResponse().asString();
                assertTrue(body.contains("# HELP"), "Expected Prometheus text exposition");
                assertTrue(body.contains("jvm_") || body.contains("process_"), "Expected JVM or process metrics");
            }
        }).run();
    }

    public void inf009() {
        run().step("SELECT only, through the existing DbCustomFlow and scheduler DB steps", flow -> {
            var db = flow.dbCustomSteps().schedulerSteps();
            var ping = db.query("SELECT 1 AS probe");
            assertEquals(1, ping.size());
            assertEquals(1, ((Number) ping.get(0).get("probe")).intValue());
            var rows = db.query("SELECT table_name FROM information_schema.tables "
                    + "WHERE table_schema='scheduler' AND table_name IN "
                    + "('task','task_action','field_dict','field_operator_dict','field_enum_dict') ORDER BY table_name");
            Set<String> names = rows.stream().map(row -> row.get("table_name").toString()).collect(Collectors.toSet());
            assertTrue(names.containsAll(Set.of("task", "task_action", "field_dict", "field_operator_dict", "field_enum_dict")),
                    "The selected framework DB must expose the scheduler baseline tables");
            KubernetesTunnelSteps.evidence("Framework DB schema inventory", Map.of(
                    "environment", TestEnvironment.current(), "client", "explab", "tables", names));
        }).run();
    }

    public void inf010() {
        run().step("Prove mTLS business REST and Fabric8 actuator transport separation", flow -> {
            assertAll("Both ingress and Kubernetes observations are required",
                    () -> {
                        awaitReadyMtlsIngress(flow);
                        JsonNode registry = expect(flow.restCustomSteps().schedulerSteps()
                                .registry(Map.of("page", 0, "size", 1)), 200);
                        assertTrue(registry.path("content").isArray());
                    },
                    () -> requireNativeActuatorReady(flow));
            KubernetesTunnelSteps.evidence("Scheduler transport separation", Map.of(
                    "businessRest", "strict mTLS ingress",
                    "kubernetesManagement", "ContainerService/Fabric8",
                    "actuator", "Fabric8 pod port-forward",
                    "ocFallback", false));
        }).run();
    }

    /**
     * Shared regression preflight: transport and backend readiness only. Business contract assertions
     * remain in regression scenarios so a service defect cannot be converted into a disabled test.
     */
    public void regressionTransportBaseline() {
        run().step("Require strict mTLS ingress and Fabric8 actuator readiness before scheduler regression", flow -> {
            assertAll("Both transports must be ready before regression",
                    () -> awaitReadyMtlsIngress(flow),
                    () -> requireNativeActuatorReady(flow));
            KubernetesTunnelSteps.evidence("Scheduler regression transport baseline", Map.of(
                    "businessRest", "strict mTLS ingress",
                    "kubernetesManagement", "ContainerService/Fabric8",
                    "actuator", "Fabric8 pod port-forward",
                    "mutationsDispatched", false,
                    "credentialsAttached", false));
        }).run();
    }

    public void inf011() {
        run().step("Exercise the same port ownership guard used by both transports", flow -> {
            try (ServerSocket ownedControl = new ServerSocket()) {
                ownedControl.bind(new InetSocketAddress("127.0.0.1", 0));
                int port = ownedControl.getLocalPort();
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        () -> KubernetesTunnelService.requireFreePort(port));
                assertTrue(failure.getMessage().startsWith("PORT_IN_USE:"));
                assertFalse(ownedControl.isClosed());
                assertTrue(KubernetesTunnelService.acceptsConnections(port));
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot bind the diagnostic control listener", failure);
            }
        }).run();
    }

    public void inf012() {
        run().step("Check native resource ownership and explicit reopening", flow -> {
            var infrastructure = flow.infrastructureSteps();
            TunnelSession first = infrastructure.openNative();
            String firstId = first.id();
            int firstPort = first.localPort();
            try {
                assertSame(first, infrastructure.openNative(), "Do not allocate another listener per REST request");
                JsonNode health = expect(flow.schedulerSteps(first).call("GET", HEALTH, null), 200);
                assertEquals("UP", health.path("status").asText());
            } finally {
                first.close();
            }
            assertTrue(first.isClosed());
            assertFalse(first.isAlive());
            assertTrue(KubernetesTunnelService.awaitListenerClosed(firstPort, 5), "Listener remained open after close()");
            try (TunnelSession reopened = infrastructure.openNative()) {
                assertNotEquals(firstId, reopened.id());
                assertTrue(reopened.isAlive());
                JsonNode health = expect(flow.schedulerSteps(reopened).call("GET", HEALTH, null), 200);
                assertEquals("UP", health.path("status").asText());
            }
        }).run();
    }

    public void inf013() {
        run().step("Capture V2 ASC/DESC evidence through mTLS; regression owns the service assertion", flow -> {
                awaitReadyMtlsIngress(flow);
                SchedulerRegistrySteps registry = flow.restCustomSteps().schedulerRegistrySteps();
                var fields = registry.metadata();
                var baseline = registry.baseline();
                var selected = registry.chooseSort(fields, baseline);
                assumeTrue(selected.isPresent(),
                        "No mapped V2 sortable field with non-null varied baseline values; no direction coverage claimed");
                var field = selected.orElseThrow();
                var ascending = registry.sorted(field, "ASC");
                var descending = registry.sorted(field, "DESC");
                assertFalse(ascending.isEmpty(), "ASC response unexpectedly lost the observed registry rows");
                assertFalse(descending.isEmpty(), "DESC response unexpectedly lost the observed registry rows");
                assertTrue(ascending.size() <= SchedulerRegistrySteps.PAGE_SIZE
                        && descending.size() <= SchedulerRegistrySteps.PAGE_SIZE);
                var asc = ascending.stream().map(field::value).toList();
                var desc = descending.stream().map(field::value).toList();
                KubernetesTunnelSteps.evidence("V2 ASC/DESC evidence", Map.of(
                        "formCode", SchedulerRegistrySteps.FORM_CODE, "paramCode", field.code(),
                        "dtoPointer", field.pointer(), "ascendingRows", asc.size(), "descendingRows", desc.size(),
                        "scope", "Bounded pages on a shared stand; no timestamp-unit conversion",
                        "taskNumberMappingScenario", "SCH-INF-014"));
                KubernetesTunnelSteps.evidence("V2 ASC rows (bounded exact oracle values)", sortRows(ascending, field));
                KubernetesTunnelSteps.evidence("V2 DESC rows (bounded exact oracle values)", sortRows(descending, field));
                assumeTrue(asc.stream().noneMatch(JsonNode::isNull) && desc.stream().noneMatch(JsonNode::isNull),
                        "Extreme pages contain nulls; null-order semantics are not inferred by this diagnostic");
                List<Integer> ascSigns = adjacentSigns(asc, field);
                List<Integer> descSigns = adjacentSigns(desc, field);
                KubernetesTunnelSteps.evidence("V2 sort service-defect observation", Map.of(
                        "field", field.code(), "ascendingSigns", ascSigns, "descendingSigns", descSigns,
                        "blockingAssertionOwner", "SCH-RO-004/SCH-RO-005"));
                System.out.println("[scheduler-diagnostic] SCH-INF-013 field=" + field.code()
                        + ", ascendingSigns=" + ascSigns + ", descendingSigns=" + descSigns);
        }).run();
    }

    public void inf014() {
        run().step("Capture taskNumber API/DB evidence through mTLS; regression owns the service assertion", flow -> {
                awaitReadyMtlsIngress(flow);
                JsonNode response = expect(flow.restCustomSteps().schedulerSteps().registry(
                        Map.of("page", 0, "size", SchedulerRegistrySteps.PAGE_SIZE)), 200);
                JsonNode content = response.path("content");
                assertTrue(content.isArray() && content.size() <= SchedulerRegistrySteps.PAGE_SIZE);
                assumeTrue(!content.isEmpty(), "No V2 rows available for the persisted taskNumber check");
                Map<Long, JsonNode> byId = new LinkedHashMap<>();
                for (JsonNode row : content) {
                    JsonNode id = row.path("id");
                    assertTrue(id.isIntegralNumber() && id.canConvertToLong() && id.asLong() > 0);
                    assertFalse(byId.containsKey(id.asLong()), "Duplicate registry row id");
                    JsonNode number = row.path("taskNumber");
                    assertTrue(number.isNull() || (number.isIntegralNumber() && number.canConvertToLong()),
                            "taskNumber must preserve a nullable int64 value");
                    byId.put(id.asLong(), number);
                }
                var persisted = flow.dbCustomSteps().schedulerSteps()
                        .registryTaskNumbers(new ArrayList<>(byId.keySet()));
                assertEquals(byId.keySet(), persisted.keySet(),
                        "API ids are absent from the configured V2 DB: inspect namespace/DB selection or deletion");
                int mismatches = 0;
                int witnessesAgainstZero = 0;
                List<Map<String, Object>> mismatchRows = new ArrayList<>();
                for (var entry : byId.entrySet()) {
                    Long expected = persisted.get(entry.getKey());
                    Long actual = entry.getValue().isNull() ? null : entry.getValue().asLong();
                    if (expected == null || expected != 0L) witnessesAgainstZero++;
                    if (!java.util.Objects.equals(expected, actual)) {
                        mismatches++;
                        // Explicit types and text values preserve NULL in the attachment without normalization.
                        mismatchRows.add(Map.of(
                                "id", entry.getKey(),
                                "expectedDbType", expected == null ? "NULL" : "INT64",
                                "expectedDbValue", expected == null ? "NULL" : expected.toString(),
                                "actualApiType", actual == null ? "NULL" : "INT64",
                                "actualApiValue", actual == null ? "NULL" : actual.toString(),
                                "kind", expected == null ? "DB_NULL_API_NUMBER"
                                        : actual == null ? "DB_NUMBER_API_NULL" : "VALUE_MISMATCH"));
                    }
                }
                KubernetesTunnelSteps.evidence("V2 taskNumber persistence comparison", Map.of(
                        "environment", TestEnvironment.current(), "databaseClient", "explab",
                        "comparedRows", byId.size(), "mismatchedRows", mismatches,
                        "persistedNonZeroOrNullWitnesses", witnessesAgainstZero,
                        "scope", "Observed ids only, SELECT only; service and DB must be the same stand",
                        "note", "No taskNumber rewrite, task creation or job pause"));
                KubernetesTunnelSteps.evidence("V2 taskNumber mismatch rows", Map.of(
                        "expectedSource", "scheduler.task.task_number via framework explab DB client",
                        "actualSource", "POST /api/v2/schedule/tasks: content[].taskNumber",
                        "comparedRows", byId.size(), "mismatchCount", mismatches,
                        "rowLimit", SchedulerRegistrySteps.PAGE_SIZE, "rows", mismatchRows,
                        "valueEncoding", "Text plus explicit NULL/INT64 type; no NULL-to-zero conversion",
                        "scope", "Corporate task IDs and values; review before exporting attachments"));
                System.out.println("[scheduler-diagnostic] SCH-INF-014 comparedRows=" + byId.size()
                        + ", mismatchCount=" + mismatches
                        + ", persistedNonZeroOrNullWitnesses=" + witnessesAgainstZero
                        + ", blockingAssertionOwner=SCH-RO-007");
        }).run();
    }

    private static List<Map<String, Object>> sortRows(List<JsonNode> rows, SchedulerRegistrySteps.Field field) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode value = field.value(row);
            evidence.add(Map.of("id", row.path("id").asText(), "field", field.code(),
                    "type", value.getNodeType().name(), "value", value.isNull() ? "NULL" : value.asText()));
        }
        return evidence;
    }

    private static List<Integer> adjacentSigns(List<JsonNode> values, SchedulerRegistrySteps.Field field) {
        List<Integer> signs = new ArrayList<>();
        for (int index = 1; index < values.size(); index++)
            signs.add(Integer.signum(field.compare(values.get(index - 1), values.get(index))));
        return signs;
    }

    private static FlowRunner<SchedulerInfrastructureFlow> run() {
        return FlowRunner.flowRunnerFor(SchedulerInfrastructureFlow.class);
    }

    private static void requireNativeActuatorReady(SchedulerInfrastructureFlow flow) {
        try (TunnelSession session = flow.infrastructureSteps().openNative()) {
            assertTrue(session.isAlive(), "Fabric8 port-forward must target a current Ready scheduler pod");
            JsonNode health = expect(flow.schedulerSteps(session).call("GET", HEALTH, null), 200);
            assertEquals("UP", health.path("status").asText(), "Scheduler health must be UP before regression");
            assertEquals("UP", health.path("components").path("db").path("status").asText(),
                    "Scheduler DB health must be UP before regression");
        }
    }

    private static void awaitReadyMtlsIngress(SchedulerInfrastructureFlow flow) {
        String blocker = MTLS_INGRESS_BLOCKER.get();
        if (blocker != null)
            throw new IllegalStateException("CASCADE_FROM_MTLS_INGRESS: " + blocker
                    + "; no business assertion was executed");
        // This HTTP probe must remain runnable when Kubernetes authentication is rejected.
        KubernetesTunnelSteps.evidence("mTLS probe independence", Map.of(
                "kubernetesReadinessRequired", false, "kubernetesAuthenticationChecked", false));
        long started = System.nanoTime();
        long deadline = started + java.util.concurrent.TimeUnit.SECONDS.toNanos(MTLS_STABILIZATION_SECONDS);
        int attempts = 0;
        AssertionError lastFailure = null;
        while (System.nanoTime() < deadline) {
            attempts++;
            try {
                expect(flow.restCustomSteps().schedulerSteps().call("GET", FILTERS, null), 200);
                KubernetesTunnelSteps.evidence("mTLS ingress stabilization", Map.of(
                        "status", "READY", "attempts", attempts,
                        "elapsedMillis", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - started),
                        "probe", FILTERS, "businessAssertionsEnabled", true));
                return;
            } catch (AssertionError failure) {
                String message = failure.getMessage() == null ? "" : failure.getMessage();
                if (!(message.contains("502") || message.contains("503") || message.contains("504"))) throw failure;
                lastFailure = failure;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("MTLS_INGRESS_STABILIZATION_INTERRUPTED", interrupted);
                }
            }
        }
        String root = "INGRESS_UPSTREAM_UNAVAILABLE: strict mTLS route returned 502/503/504 for "
                + MTLS_STABILIZATION_SECONDS + " seconds; pod readiness not established by this independent HTTP probe; attempts=" + attempts;
        MTLS_INGRESS_BLOCKER.compareAndSet(null, root);
        KubernetesTunnelSteps.evidence("mTLS ingress stabilization", Map.of(
                "status", "UNAVAILABLE", "attempts", attempts,
                "elapsedMillis", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                "probe", FILTERS, "businessAssertionsExecuted", false,
                "classification", "INGRESS_UPSTREAM_UNAVAILABLE"));
        throw new IllegalStateException(root, lastFailure);
    }

    private static Map<String, String> shapes(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            result.put(entry.getKey(), value.getNodeType().name());
        });
        return result;
    }
}
