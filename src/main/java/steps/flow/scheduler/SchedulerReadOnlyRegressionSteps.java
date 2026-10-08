package steps.flow.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.special.EnvironmentConfigWithScheduler;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.scheduler.AbstractSchedulerFlowTest;
import ru.sber.qa.matchers.RestMatchers;
import steps.container.KubernetesTunnelSteps;
import steps.rest.scheduler.SchedulerRegistrySteps;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static steps.rest.scheduler.SchedulerSteps.expect;


/** Source-backed reusable flow steps; no JUnit scenario discovery here. */
public final class SchedulerReadOnlyRegressionSteps extends SchedulerScenarioSupport {
    public void ro001() {
        getFlowWithRest()
                .step("Read scheduler actuator endpoints through the internal service tunnel",
                        flow -> new SchedulerWorkloadDiagnosticSteps()
                                .assertHealthyAndPrometheus("read-only-regression"))
                .run();
    }

    public void ro002() {
        getFlowWithRest()
                .step("Read the real V2 dictionary and one bounded registry page", flow -> {
                    var registry = flow.restCustomSteps().schedulerRegistrySteps();
                    assertFalse(registry.metadata().isEmpty(), "No source-backed V2 fields in the dictionary");
                    registry.baseline(); // An empty page is valid for this structural check only.
                }).run();
    }

    public void ro003() {
        getFlowWithRest()
                .step("Choose an advertised filter and check its result", flow -> {
                    var registry = flow.restCustomSteps().schedulerRegistrySteps();
                    var fields = registry.metadata();
                    var selected = registry.chooseFilter(fields, registry.baseline());
                    assumeTrue(selected.isPresent(), "No usable existing row/filter; no filtering coverage claimed");
                    var choice = selected.orElseThrow();
                    var rows = registry.filtered(choice);
                    if (rows.isEmpty()) {
                        boolean witnessStillVisible = registry.baseline().stream().anyMatch(row ->
                                !choice.field().value(row).isNull()
                                && choice.field().compare(choice.value(), choice.field().value(row)) == 0);
                        if (!witnessStillVisible)
                            throw new IllegalStateException("READ_ONLY_WITNESS_CHANGED: no stable cross-request witness; no filtering verdict");
                        assertFalse(rows.isEmpty(), "Equal filter lost a witness still visible in the independent control");
                    }
                    assertAll("Every returned row must satisfy the filter", rows.stream()
                            .map(row -> (Executable) () -> assertEquals(0,
                                    choice.field().compare(choice.value(), choice.field().value(row)))));
                    KubernetesTunnelSteps.evidence("Read-only filter result",
                            Map.of("field", choice.field().code(), "matchedRows", rows.size()));
                }).run();
    }

    public void ro004() {
        getFlowWithRest().step("Check ASC independently from DESC", flow ->
                checkDirection(flow.restCustomSteps().schedulerRegistrySteps(), "ASC")).run();
    }

    public void ro005() {
        getFlowWithRest().step("Check DESC independently from ASC", flow ->
                checkDirection(flow.restCustomSteps().schedulerRegistrySteps(), "DESC")).run();
    }

    public void ro006() {
        getFlowWithRest().step("Check date wire types without normalizing numeric values to strings", flow -> {
            var rows = flow.restCustomSteps().schedulerRegistrySteps().baseline();
            assumeTrue(!rows.isEmpty(), "No existing task; date contract coverage is not claimed");
            List<Map<String, Object>> shapes = new ArrayList<>();
            List<Executable> checks = new ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                for (String field : List.of("planDt", "createdAt", "updatedAt")) {
                    JsonNode value = rows.get(index).path(field);
                    String location = "content[" + index + "]." + field;
                    shapes.add(Map.of("path", location, "type", value.getNodeType().name()));
                    checks.add(() -> {
                        util.scheduler.SchedulerAssertions.assertDocumentedEpochInteger(value, location);
                    });
                }
            }
            KubernetesTunnelSteps.evidence("Registry date field types", shapes);
            assertAll("ExpLab v17 documented integer date contract", checks);
        }).run();
    }

    public void ro007() {
        getFlowWithDbRest().step("Check the documented registry taskNumber constant and persisted task ids", flow -> {
            var rows = flow.restCustomSteps().schedulerRegistrySteps().baseline();
            assertFalse(rows.isEmpty(), "No observed V2 task: registry/DB correlation is not proven");
            Map<Long, Long> observed = new LinkedHashMap<>();
            List<Executable> checks = new ArrayList<>();
            for (JsonNode row : rows) {
                JsonNode id = row.path("id");
                assertTrue(id.isIntegralNumber() && id.canConvertToLong() && id.asLong() > 0,
                        "Registry id must be a positive int64");
                assertFalse(observed.containsKey(id.asLong()), "Duplicate registry id");
                observed.put(id.asLong(), null);
                JsonNode number = row.path("taskNumber");
                // Optional in the registry; if returned, p.2652 specifies constant 0, not the DB value.
                checks.add(() -> assertTrue(number.isMissingNode()
                                || (number.isIntegralNumber() && number.canConvertToLong() && number.asLong() == 0L),
                        "ExpLab v17 p.2652: taskNumber is optional; if present it must be integer zero, id=" + id));
            }
            assertAll("Registry taskNumber wire contract", checks);
            var db = flow.dbCustomSteps().schedulerSteps().registryTaskNumbers(new ArrayList<>(observed.keySet()));
            // API and SQL are not one transaction. Do not turn an unproven cross-request snapshot into a service defect.
            if (!observed.keySet().equals(db.keySet()))
                throw new IllegalStateException("READ_ONLY_CORRELATION_UNCONFIRMED: API/DB snapshots differ; inspect concurrent deletion or target selection");
            KubernetesTunnelSteps.evidence("Documented registry taskNumber contract", Map.of(
                    "observedRows", observed.size(), "persistedIds", db.size(),
                    "contract", "ExpLab v17 p.2652: optional constant zero; DB task_number is not the registry oracle"));
            assertAll("Registry taskNumber contract and persisted ids", checks);
        }).run();
    }

    private static void checkDirection(SchedulerRegistrySteps registry, String direction) {
        var fields = registry.metadata();
        var selected = registry.chooseSort(fields, registry.baseline());
        assumeTrue(selected.isPresent(), "No sortable field with varied baseline values; no sorting coverage claimed");
        var field = selected.orElseThrow();
        var rows = registry.sorted(field, direction);
        assertFalse(rows.isEmpty(), direction + " lost existing rows; inspect concurrent changes");
        var values = rows.stream().map(field::value).toList();
        assumeTrue(values.stream().noneMatch(JsonNode::isNull), "Null ordering is outside this contract probe");
        List<Integer> comparisons = new ArrayList<>();
        for (int index = 1; index < values.size(); index++)
            comparisons.add(Integer.signum(field.compare(values.get(index - 1), values.get(index))));
        KubernetesTunnelSteps.evidence("Read-only " + direction + " adjacent comparisons", Map.of(
                "field", field.code(), "dtoPointer", field.pointer(), "rowCount", rows.size(),
                "adjacentComparisonSigns", comparisons,
                "legend", "-1: left < right; 0: equal; 1: left > right; no personal values attached"));
        System.out.println("[scheduler-readonly-debug] sort field=" + field.code()
                + ", direction=" + direction + ", dtoPointer=" + field.pointer()
                + ", rowCount=" + rows.size() + ", adjacentSigns=" + comparisons);
        List<Executable> checks = new ArrayList<>();
        for (int index = 0; index < comparisons.size(); index++) {
            int position = index;
            checks.add(() -> assertTrue("ASC".equals(direction)
                            ? comparisons.get(position) <= 0 : comparisons.get(position) >= 0,
                    direction + " violation at adjacent rows " + position + "/" + (position + 1)));
        }
        assertAll(direction + " monotonicity", checks);
        assumeTrue(comparisons.stream().anyMatch(value -> value != 0),
                "Only equal values on this page; direction coverage is not claimed");
    }
}
