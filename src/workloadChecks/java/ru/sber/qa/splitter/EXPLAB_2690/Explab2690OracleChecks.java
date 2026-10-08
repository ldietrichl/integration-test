package ru.sber.qa.splitter.EXPLAB_2690;

import util.splittercheck.WorkedGroupAssertions;
import steps.flow.splitter.workedgroup.WorkedGroupSteps;
import ru.sber.qa.splitter.extension.MapperKafkaReportContractFlowTest;
import ru.sber.qa.splitter.extension.ReactionsKafkaReportContractFlowTest;
import ru.sber.qa.splitter.extension.SplitterDocumentContractChecks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dto.splitter.common.ParamDto;
import org.opentest4j.MultipleFailuresError;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public final class Explab2690OracleChecks {
    public static void main(String[] ignored) throws Exception {
        var json = new ObjectMapper();
        var object = (ObjectNode) json.readTree("""
                {"objectId":"one","objectResults":[{"ruleCode":"MAIN"},{"ruleCode":"ALL"}]}
                """);
        var root = json.createObjectNode();
        root.putArray("splittingResults").add(object);
        WorkedGroupAssertions.objects(root, List.of("one"));
        var extras = root.deepCopy(); extras.withArray("splittingResults").addObject().put("objectId", "extra");
        assertThrows(AssertionError.class, () -> WorkedGroupAssertions.objects(extras, List.of("one")));
        assertThrows(AssertionError.class, () -> WorkedGroupAssertions.objects(root, List.of("missing")));
        var duplicate = root.deepCopy(); duplicate.withArray("splittingResults").add(object);
        assertThrows(AssertionError.class, () -> WorkedGroupAssertions.objects(duplicate, List.of("one")));
        WorkedGroupAssertions.rules(object, Set.of("MAIN", "ALL"));
        for (String rule : List.of("MAIN", "OTHER")) {
            var bad = object.deepCopy(); bad.withArray("objectResults").addObject().put("ruleCode", rule);
            assertThrows(AssertionError.class, () -> WorkedGroupAssertions.rules(bad, Set.of("MAIN", "ALL")));
        }
        for (String flags : List.of("[]", "null", "[{\"code\":\"filtered\",\"value\":\"false\"}]"))
            WorkedGroupAssertions.noMainFlags(json.readTree(flags));
        WorkedGroupAssertions.mainFlags(json.readTree("[{\"code\":\"isAlternative\",\"value\":null}]"));
        var wrongMain = json.readTree("[{\"code\":\"isAlternative\",\"value\":\"false\"}]");
        assertThrows(AssertionError.class, () -> WorkedGroupAssertions.mainFlags(wrongMain));
        for (String flags : List.of("{}", "[{\"code\":\"filtered\",\"value\":\"true\"}]",
                "[{\"code\":\"filtered\",\"value\":false}]", "[{\"code\":\"other\",\"value\":\"false\"}]",
                "[{\"code\":\"filtered\",\"value\":\"false\"},{\"code\":\"filtered\",\"value\":\"false\"}]")) {
            var bad = json.readTree(flags);
            assertThrows(AssertionError.class, () -> WorkedGroupAssertions.noMainFlags(bad));
        }
        List<ParamDto> expected = List.of(new ParamDto("actionType", List.of("1"), "INTEGER"),
                new ParamDto("result", List.of("a", "b"), "STRING"));
        var exp = json.createObjectNode().set("groupResultParams", json.valueToTree(expected));
        WorkedGroupAssertions.parameters(exp, expected);
        for (String field : List.of("dataType", "paramCode", "paramValues", "duplicate", "extra", "missing")) {
            ObjectNode bad = exp.deepCopy();
            var array = bad.withArray("groupResultParams");
            switch (field) {
                case "dataType" -> ((ObjectNode) array.get(0)).put(field, "STRING");
                case "paramCode" -> ((ObjectNode) array.get(0)).put(field, "wrong");
                case "paramValues" -> ((ObjectNode) array.get(0)).withArray(field).add("extra");
                case "duplicate" -> array.add(array.get(0).deepCopy());
                case "extra" -> array.addObject().put("paramCode", "extra");
                case "missing" -> array.remove(0);
            }
            assertThrows(AssertionError.class, () -> WorkedGroupAssertions.parameters(bad, expected));
        }
        var failures = assertThrows(MultipleFailuresError.class, () -> assertAll("objects",
                () -> fail("object 1"), () -> fail("object 2"), () -> fail("object 3")));
        assertEquals(3, failures.getFailures().size());
        reactionsRules();
        noMainRestRules(json);
        requestTimes(json);
        reportErrors(json);
        SplitterDocumentContractChecks.main(new String[0]);
        util.support.SplitterRuntimeMetadataChecks.main(new String[0]);
        fixtureCount();
        System.out.println("EXPLAB-2690 oracle checks passed (exact IDs/rules/params, no-MAIN flags, independent errors)");
    }

    private static void reactionsRules() throws Exception {
        var yaml = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
        try (var stream = Explab2690OracleChecks.class.getClassLoader().getResourceAsStream(
                "splitter/EXPLAB_2690/configmap/reactions-required.yml")) {
            assertNotNull(stream);
            var root = yaml.readTree(stream);
            assertEquals(Set.of("rules"), keys(root), "REACTIONS rules root has no mapper-only properties");
            var rule = root.path("rules").path("final-exp-rule");
            assertEquals("MAIN", rule.path("rule-code").textValue());
            assertEquals("finalExpByLayerAndId", rule.path("proc-code").textValue());
            assertTrue(rule.path("proc-params").path("max-layer-priority").booleanValue());
            assertEquals(com.fasterxml.jackson.databind.node.BooleanNode.FALSE, rule.path("proc-params").path("max-id"));
        }
    }

    private static Set<String> keys(com.fasterxml.jackson.databind.JsonNode node) {
        Set<String> keys = new HashSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static void noMainRestRules(ObjectMapper json) {
        for (boolean reactions : List.of(false, true)) {
            for (boolean allow : List.of(false, true)) {
                for (boolean hasWorked : List.of(false, true)) {
                    boolean includeAll = allow && hasWorked;
                    var object = json.createObjectNode().put("objectId", "one");
                    var rows = object.putArray("objectResults");
                    if (allow) rows.addObject().put("ruleCode", "MAIN").putArray("resultExps");
                    if (includeAll) rows.addObject().put("ruleCode", "ALL");
                    WorkedGroupAssertions.noMainRestRules(object, reactions, allow, hasWorked);
                    object.putArray("objectFlags").addObject().put("code", "filtered").put("value", "false");
                    WorkedGroupAssertions.noMainRestRules(object, reactions, allow, hasWorked);
                    var main = object.deepCopy();
                    main.withArray("objectResults").addObject().put("ruleCode", "MAIN").putArray("resultExps");
                    assertThrows(AssertionError.class, () ->
                            WorkedGroupAssertions.noMainRestRules(main, reactions, allow, hasWorked));
                    var extra = object.deepCopy();
                    extra.withArray("objectResults").addObject().put("ruleCode", "ALL");
                    assertThrows(AssertionError.class, () ->
                            WorkedGroupAssertions.noMainRestRules(extra, reactions, allow, hasWorked));
                    if (allow) {
                        for (String shape : List.of("missing", "null", "object", "nonempty")) {
                            var bad = object.deepCopy();
                            var badMain = (ObjectNode) bad.path("objectResults").get(0);
                            switch (shape) {
                                case "missing" -> badMain.remove("resultExps");
                                case "null" -> badMain.putNull("resultExps");
                                case "object" -> badMain.putObject("resultExps");
                                default -> badMain.withArray("resultExps").addObject().put("expId", 1);
                            }
                            assertThrows(AssertionError.class, () ->
                                    WorkedGroupAssertions.noMainRestRules(bad, reactions, allow, hasWorked));
                        }
                        object.remove("objectResults");
                        assertThrows(AssertionError.class, () ->
                                WorkedGroupAssertions.noMainRestRules(object, reactions, allow, hasWorked));
                    } else {
                        object.remove("objectResults");
                        WorkedGroupAssertions.noMainRestRules(object, reactions, allow, hasWorked);
                        object.putNull("objectResults");
                        WorkedGroupAssertions.noMainRestRules(object, reactions, allow, hasWorked);
                    }
                }
            }
        }
        System.out.println("EXPLAB-2690 no-MAIN REST checks: 8 endpoint/flag/worked-group combinations passed");
    }

    private static void requestTimes(ObjectMapper json) throws Exception {
        var absent = json.createObjectNode().path("requestDt");
        var integer = json.getNodeFactory().numberNode(1_000);
        var longValue = json.getNodeFactory().numberNode(1_000L);
        for (var api : List.of(absent, integer)) {
            for (var report : List.of(absent, longValue)) {
                WorkedGroupAssertions.requestTime(api, 1000, 1000);
                WorkedGroupAssertions.requestTime(report, 1000, 1000);
                WorkedGroupAssertions.correlateRequestTimes(api, report);
            }
        }
        for (String invalid : List.of("null", "\"1000\"", "true", "1000.0", "{}", "[]",
                "9223372036854775808", "-9223372036854775809", "999999", "-999999")) {
            var value = json.readTree(invalid);
            assertThrows(AssertionError.class, () -> WorkedGroupAssertions.requestTime(value, 1000, 1000), invalid);
        }
        WorkedGroupAssertions.requestTime(json.getNodeFactory().numberNode(700_000L), 1_000_000, 1_000_100);
        WorkedGroupAssertions.requestTime(json.getNodeFactory().numberNode(1_300_100L), 1_000_000, 1_000_100);
        for (long outside : List.of(699_999L, 1_300_101L))
            assertThrows(AssertionError.class, () -> WorkedGroupAssertions.requestTime(
                    json.getNodeFactory().numberNode(outside), 1_000_000, 1_000_100));
        assertThrows(AssertionError.class, () -> WorkedGroupAssertions.correlateRequestTimes(integer,
                json.getNodeFactory().numberNode(1001L)));
        System.out.println("EXPLAB-2690 requestDt checks: optional presence, long types, window and correlation passed");
    }

    private static void reportErrors(ObjectMapper json) {
        var experiment = new dto.splitter.config.ExperimentDto();
        experiment.setId(269090);
        experiment.setSalt("test");
        experiment.setGroups(java.util.stream.IntStream.range(0, 3).mapToObj(i ->
                new dto.splitter.config.GroupDto(String.valueOf((char) ('A' + i)), List.of(),
                        List.of(new dto.splitter.config.SplittingResultDto(i + 1, List.of())))).toList());
        var report = json.createObjectNode().put("resultDt", 1_000_000).put("requestDt", 1000);
        var object = report.putArray("splittingResults").addObject().put("objectId", WorkedGroupSteps.SINGLE_OBJECT_ID);
        var rules = object.putArray("objectResults");
        var all = rules.addObject().put("ruleCode", "ALL").putArray("resultExps");
        for (int i = 0; i < 3; i++) all.addObject().put("expId", 269090).put("expGroup", String.valueOf((char) ('A' + i)))
                .put("conditionId", i + 1).put("salt", "test").put("spreadValue", 8500).putNull("finalExpGroup");
        var api = report.deepCopy();
        var checker = new MapperKafkaReportContractFlowTest();
        checker.verifyReportBody(report, api, experiment, false, "NONE", 8500, 1000, 1000);
        for (boolean restTime : List.of(false, true)) {
            for (boolean reportTime : List.of(false, true)) {
                var optionalApi = api.deepCopy();
                var optionalReport = report.deepCopy();
                if (!restTime) optionalApi.remove("requestDt");
                if (!reportTime) optionalReport.remove("requestDt");
                checker.verifyReportBody(optionalReport, optionalApi, experiment, false, "NONE", 8500, 1000, 1000);
            }
        }
        report.remove("requestDt");
        rules.addObject().put("ruleCode", "MAIN").putArray("resultExps");
        ((ObjectNode) all.get(0)).put("salt", "wrong-A");
        ((ObjectNode) all.get(1)).put("salt", "wrong-B");
        var error = assertThrows(MultipleFailuresError.class, () ->
                checker.verifyReportBody(report, api, experiment, false, "NONE", 8500, 1000, 1000));
        List<Throwable> leaves = new ArrayList<>();
        collect(error, leaves);
        assertEquals(3, leaves.size(), "Optional time must not hide extra MAIN or two bad rows");
        assertTrue(leaves.stream().anyMatch(e -> e.getMessage().contains("Exact rules")));
        assertEquals(2, leaves.stream().filter(e -> e.getMessage().contains("salt")).count());
        report.put("requestDt", "wrong-type");
        var invalidTime = assertThrows(MultipleFailuresError.class, () ->
                checker.verifyReportBody(report, api, experiment, false, "NONE", 8500, 1000, 1000));
        leaves.clear();
        collect(invalidTime, leaves);
        assertEquals(4, leaves.size(), "Present invalid time, extra MAIN and two bad rows must all be reported");
        assertTrue(leaves.stream().anyMatch(e -> e.getMessage().contains("epoch milliseconds")));
        assertEquals("MAPPER", runtimePoint(checker));
        assertEquals("REACTIONS", runtimePoint(new ReactionsKafkaReportContractFlowTest()));
        System.out.println("EXPLAB-2690 report checks: independent timestamp/rules/row failures passed");
    }

    private static String runtimePoint(WorkedGroupSteps steps) {
        try {
            var method = WorkedGroupSteps.class.getDeclaredMethod("runtimeSplittingPoint");
            method.setAccessible(true);
            return (String) method.invoke(steps);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect runtime splitting point", failure);
        }
    }

    private static void collect(Throwable error, List<Throwable> leaves) {
        if (error instanceof MultipleFailuresError multiple) multiple.getFailures().forEach(e -> collect(e, leaves));
        else leaves.add(error);
    }

    private static void fixtureCount() throws Exception {
        int total = 0;
        int mapper = 0;
        int core = 0;
        int coreMapper = 0;
        Set<Class<?>> classes = new HashSet<>();
        for (String name : List.of("SplitterMapperWorkedGroup2690FlowTest", "SplitterMapperAlternative2690FlowTest",
                "SplitterMapperNoMain2690FlowTest", "SplitterMapperMixedObjects2690FlowTest",
                "SplitterMapperNoMainDenied2690FlowTest", "SplitterMapperWorkedGroupDenied2690FlowTest",
                "SplitterMapperMixedObjectsDenied2690FlowTest", "MapperKafkaReportContractFlowTest",
                "SplitterReactionsNoAlternative2690FlowTest", "SplitterReactionsFinalExperiments2690FlowTest", "ReactionsMultipleFinalExperimentsFlowTest",
                "SplitterReactionsGroups2690FlowTest", "SplitterReactionsGroupsDenied2690FlowTest",
                "ReactionsLayerPriorityFlowTest", "ReactionsLayerPriorityWithoutMainDeniedFlowTest",
                "SplitterReactionsNoAlternativeDenied2690FlowTest", "ReactionsKafkaReportContractFlowTest",
                "MapperDocumentMatrixFlowTest", "MapperDocumentMatrixWithoutMainDeniedFlowTest",
                "ReactionsDocumentMatrixFlowTest", "ReactionsDocumentMatrixWithoutMainDeniedFlowTest")) {
            boolean extension = !name.startsWith("Splitter");
            Class<?> type = Class.forName((extension ? "ru.sber.qa.splitter.extension" : Explab2690OracleChecks.class.getPackageName()) + "." + name);
            assertTrue(classes.add(type), "Duplicate scenario class");
            assertEquals(extension, type.getPackageName().endsWith(".extension"));
            boolean isMapper = name.startsWith("SplitterMapper") || name.startsWith("Mapper");
            assertTrue(java.lang.reflect.Modifier.isProtected(WorkedGroupSteps.class
                    .getDeclaredMethod("prepareExplab2690StandState").getModifiers()), "Stand hook must be inherited across packages");
            var order = type.getDeclaredAnnotation(org.junit.jupiter.api.Order.class);
            int expectedOrder = isMapper ? 10 : 30;
            if (name.contains("Denied")) expectedOrder += 10;
            if (name.equals("SplitterReactionsFinalExperiments2690FlowTest")) {
                // This retained core scenario uses JUnit's default order; do not change its lifecycle here.
                assertNull(order, "Retained T07 ordering");
            } else {
                assertNotNull(order, "Every phased class must declare its phase: " + name);
                assertEquals(expectedOrder, order.value(), name);
            }
            assertNotNull(type.getDeclaredAnnotation(io.perfeccionista.framework.SetEnvironmentConfiguration.class), name);
            int count = 0;
            for (Class<?> parent = type; parent != WorkedGroupSteps.class; parent = parent.getSuperclass()) {
                for (var method : parent.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) count++;
                    var csv = method.getAnnotation(org.junit.jupiter.params.provider.CsvSource.class);
                    if (csv != null) count += csv.value().length;
                    var values = method.getAnnotation(org.junit.jupiter.params.provider.ValueSource.class);
                    if (values != null) count += values.ints().length;
                    var source = method.getAnnotation(org.junit.jupiter.params.provider.MethodSource.class);
                    if (source != null) {
                        String reference = source.value()[0];
                        int separator = reference.indexOf('#');
                        Class<?> owner = separator < 0 ? parent : Class.forName(reference.substring(0, separator));
                        String factoryName = separator < 0 ? reference : reference.substring(separator + 1);
                        var factory = owner.getDeclaredMethod(factoryName); factory.setAccessible(true);
                        try (var stream = (java.util.stream.Stream<?>) factory.invoke(null)) { count += (int) stream.count(); }
                    }
                }
            }
            total += count;
            if (isMapper) mapper += count;
            if (!extension) { core += count; if (isMapper) coreMapper += count; }
        }
        assertEquals(49, core, "Core ticket scenarios");
        assertEquals(28, coreMapper, "Core MAPPER scenarios");
        assertEquals(125, total - core, "Extension scenarios");
        assertEquals(21, classes.size(), "Concrete classes across both suites");
        assertEquals(174, total, "Declared managed scenarios (not live execution)");
        assertEquals(84, mapper);
        System.out.println("Splitter suites: core=" + core + " (MAPPER=" + coreMapper + ", REACTIONS=" + (core - coreMapper)
                + "); extension=" + (total - core));
        System.out.println("Total declared fixtures: " + total + "; mapper=" + mapper + "; reactions=" + (total - mapper));
    }
}
