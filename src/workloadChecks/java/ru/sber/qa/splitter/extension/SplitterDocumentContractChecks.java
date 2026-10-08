package ru.sber.qa.splitter.extension;

import steps.flow.splitter.workedgroup.WorkedGroupSteps;
import util.splittercheck.WorkedGroupAssertions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.opentest4j.MultipleFailuresError;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static ru.sber.qa.splitter.extension.SplitterDocumentFixtures.*;

/** Synthetic responses exercise assertions; they are not evidence of live service conformance. */
public final class SplitterDocumentContractChecks extends WorkedGroupSteps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static int mutations;
    private static final long SENT = 1_791_000_000_000L;

    public static void main(String[] args) {
        var factory = new SplitterDocumentFixtures();
        int cases = 0;
        for (boolean reactions : List.of(false, true)) {
            List<Case> scenarios = new ArrayList<>(commonCases().toList());
            if (reactions) scenarios.addAll(layerCases().toList());
            else scenarios.addAll(filteredCases().toList());
            assertEquals(reactions ? 28 : 26, scenarios.size());
            for (boolean allow : List.of(false, true)) {
                for (Case scenario : scenarios) {
                    Fixture f = factory.create(scenario, reactions, 2690L);
                    assertTrue(f.spread() >= scenario.from() && f.spread() < scenario.to());
                    assertEquals(reactions ? "REACTIONS" : "MAPPER", f.config().getSplittingPointCode());
                    assertEquals(new HashSet<>(f.request().getSplittingObjects().stream().map(o -> o.getObjectId()).toList()), f.objects().keySet());
                    var rest = response(f, false, allow);
                    var report = response(f, true);
                    SplitterDocumentOracle.response(rest, f, false, allow);
                    SplitterDocumentOracle.response(report, f, true, allow);
                    SplitterDocumentOracle.correlation(report, rest, f, reactions ? "REACTIONS" : "MAPPER", SENT, SENT);
                    rejects(rest, f, false, allow, r -> r.withArray("splittingResults").addObject().put("objectId", "extra"));
                    rejects(report, f, true, allow, r -> r.withArray("splittingResults").remove(0));
                    rejects(report, f, true, allow, r -> r.withArray("splittingResults").add(r.path("splittingResults").get(0).deepCopy()));
                    cases++;
                }
            }
        }
        assertEquals(108, cases);
        goldenMatrix(factory);
        rowMutations(factory);
        independentObjects(factory);
        noMainWithAll(factory);
        resultTimes(factory);
        filteredFlags(factory);
        mapperProfile();
        System.out.println("Splitter extension document checks: " + cases + " parameter sets; mutation rejections=" + mutations);
    }

    private static ObjectNode response(Fixture f, boolean report) {
        return response(f, report, true);
    }

    private static ObjectNode response(Fixture f, boolean report, boolean allow) {
        var root = JSON.createObjectNode().put("requestId", f.request().getRequestId())
                .put("splittingId", f.request().getSplittingId()).put("responseId", "offline-response")
                .put("splittingConfigVersion", f.config().getConfigVersion()).put("resultDt", SENT * 1000 + 123);
        var objects = root.putArray("splittingResults");
        for (var entry : f.objects().entrySet()) {
            var object = objects.addObject().put("objectId", entry.getKey());
            object.putArray("objectFlags");
            if (!f.reactions() && !entry.getValue().main().isEmpty())
                object.withArray("objectFlags").addObject().put("code", "filtered")
                        .put("value", Boolean.toString(expectedFiltered(f, entry.getValue())));
            var rules = object.putArray("objectResults");
            if (!report && entry.getValue().main().isEmpty()) {
                if (!allow) continue;
                if (!entry.getValue().reportAll().isEmpty())
                    rules.addObject().put("ruleCode", "MAIN").putArray("resultExps");
            }
            addRule(rules, "MAIN", entry.getValue().main(), f);
            addRule(rules, "ALL", report ? entry.getValue().reportAll() : entry.getValue().restAll(), f);
        }
        return root;
    }

    private static void addRule(ArrayNode rules, String code, List<Row> expected, Fixture f) {
        if (expected.isEmpty()) return;
        var rows = rules.addObject().put("ruleCode", code).putArray("resultExps");
        for (Row row : expected) {
            var exp = f.experiment(row.expId());
            var value = rows.addObject().put("expId", row.expId()).put("expGroup", row.group())
                    .put("conditionId", row.condition()).put("finalExpGroup", row.finalGroup())
                    .put("salt", exp.getSalt()).put("spreadValue", f.spread());
            value.set("layerId", JSON.valueToTree(exp.getLayerId()));
            value.set("layerPriority", JSON.valueToTree(exp.getLayerPriority()));
            var params = exp.getGroups().stream().filter(g -> g.getCode().equals(row.group())).findFirst().orElseThrow()
                    .getSplittingResults().stream().filter(r -> r.getConditionId() == row.condition()).findFirst().orElseThrow().getResultParams();
            value.set("groupResultParams", JSON.valueToTree(params));
            var flag = value.putArray("expFlags").addObject().put("code", "isAlternative");
            if (code.equals("MAIN")) flag.putNull("value"); else flag.put("value", Boolean.toString(row.alternative()));
        }
    }

    private static ObjectNode firstRow(ObjectNode root) {
        return (ObjectNode) root.path("splittingResults").get(0).path("objectResults").get(0).path("resultExps").get(0);
    }
    private static void rejects(ObjectNode base, Fixture f, boolean report, boolean allow, Consumer<ObjectNode> mutation) {
        ObjectNode bad = base.deepCopy(); mutation.accept(bad);
        assertThrows(AssertionError.class, () -> SplitterDocumentOracle.response(bad, f, report, allow));
        mutations++;
    }
    private static void rowMutations(SplitterDocumentFixtures factory) {
        Fixture f = factory.create(new Case(Family.DIFFERENT_CONDITIONS, 0, 2500), false, 2690L);
        var report = response(f, true);
        for (String field : List.of("expId", "expGroup", "conditionId", "finalExpGroup", "salt", "spreadValue", "layerId", "layerPriority"))
            rejects(report, f, true, true, r -> firstRow(r).put(field, "wrong"));
        rejects(report, f, true, true, r -> firstRow(r).remove("finalExpGroup"));
        rejects(report, f, true, true, r -> firstRow(r).putNull("finalExpGroup"));
        rejects(report, f, true, true, r -> firstRow(r).put("conditionId", 1.0));
        rejects(report, f, true, true, r -> firstRow(r).put("expId", "269201"));
        for (String flag : List.of("unknown", "duplicate", "boolean", "invalid"))
            rejects(report, f, true, true, r -> {
                var flags = ((ObjectNode) r.path("splittingResults").get(0)).putArray("objectFlags");
                var entry = flags.addObject().put("code", flag.equals("unknown") ? "other" : "filtered");
                if (flag.equals("boolean")) entry.put("value", false); else entry.put("value", flag.equals("invalid") ? "invalid" : "false");
                if (flag.equals("duplicate")) flags.add(entry.deepCopy());
            });
        for (String field : List.of("paramCode", "dataType", "paramValues"))
            rejects(report, f, true, true, r -> ((ObjectNode) firstRow(r).path("groupResultParams").get(0)).put(field, "wrong"));
        rejects(report, f, true, true, r -> firstRow(r).withArray("groupResultParams").remove(0));
        rejects(report, f, true, true, r -> firstRow(r).withArray("groupResultParams").add(firstRow(r).path("groupResultParams").get(0).deepCopy()));
        rejects(report, f, true, true, r -> ((ObjectNode) firstRow(r).path("expFlags").get(0)).put("value", "false"));
        rejects(report, f, true, true, r -> {
            var rows = (ArrayNode) r.path("splittingResults").get(0).path("objectResults").get(1).path("resultExps");
            rows.add(rows.get(0).deepCopy());
        });
        rejects(report, f, true, true, r -> {
            var rows = (ArrayNode) r.path("splittingResults").get(0).path("objectResults").get(1).path("resultExps");
            rows.set(1, rows.get(0).deepCopy());
        });
        rejects(report, f, true, true, r -> ((ArrayNode) r.path("splittingResults").get(0).path("objectResults")).addObject().put("ruleCode", "MAIN"));
        Fixture alt = factory.create(new Case(Family.ALTERNATIVE_MATRIX, 500, 1000), false, 2690L);
        rejects(response(alt, true), alt, true, true, r -> {
            var right = (ObjectNode) r.path("splittingResults").get(1);
            var all = WorkedGroupAssertions.find(right.path("objectResults"), "ruleCode", "ALL");
            ((ObjectNode) all.path("resultExps").get(0)).putArray("expFlags");
        });
        var api = response(f, false);
        for (String field : List.of("requestId", "splittingId", "responseId", "splittingConfigVersion", "resultDt")) {
            var bad = report.deepCopy().put(field, "wrong");
            assertThrows(AssertionError.class, () -> SplitterDocumentOracle.correlation(bad, api, f, "MAPPER", SENT, SENT));
            mutations++;
        }
    }

    private static void resultTimes(SplitterDocumentFixtures factory) {
        Fixture f = factory.create(new Case(Family.DIFFERENT_CONDITIONS, 0, 2500), false, 2690);
        var api = response(f, false);
        long lower = (SENT - 300_000) * 1000;
        long upper = (SENT + 300_000) * 1000 + 999;
        for (long valid : List.of(lower, SENT * 1000, SENT * 1000 + 123, upper))
            SplitterDocumentOracle.correlation(response(f, true).put("resultDt", valid), api, f, "MAPPER", SENT, SENT);
        for (long invalid : List.of(SENT / 1000, SENT, SENT * 1_000_000, 0L, -1L,
                Long.MAX_VALUE, Long.MIN_VALUE, lower - 1, upper + 1)) {
            var bad = response(f, true).put("resultDt", invalid);
            var error = assertThrows(AssertionError.class, () ->
                    SplitterDocumentOracle.correlation(bad, api, f, "MAPPER", SENT, SENT));
            assertTrue(error.getMessage().contains("resultDt"));
            mutations++;
        }
        for (String invalid : List.of("null", "\"1791000000000000\"", "true", "1791000000000000.0",
                "[]", "{}", "9223372036854775808", "-9223372036854775809")) {
            var bad = response(f, true);
            try { bad.set("resultDt", JSON.readTree(invalid)); } catch (Exception e) { throw new AssertionError(e); }
            assertThrows(AssertionError.class, () -> SplitterDocumentOracle.correlation(bad, api, f, "MAPPER", SENT, SENT));
            mutations++;
        }
        var missing = response(f, true); missing.remove("resultDt");
        assertThrows(AssertionError.class, () -> SplitterDocumentOracle.correlation(missing, api, f, "MAPPER", SENT, SENT));
        mutations++;
        var bad = response(f, true).put("resultDt", SENT);
        ((ObjectNode) bad.path("splittingResults").get(0).path("objectFlags").get(0)).put("value", "true");
        var failure = assertThrows(MultipleFailuresError.class, () -> assertAll("time and body",
                () -> SplitterDocumentOracle.correlation(bad, api, f, "MAPPER", SENT, SENT),
                () -> SplitterDocumentOracle.response(bad, f, true, true)));
        assertEquals(2, leaves(failure).size(), "Bad resultDt must not hide a bad filtered flag");
        mutations++;
    }

    private static void filteredFlags(SplitterDocumentFixtures factory) {
        var cases = new ArrayList<>(commonCases().toList());
        cases.addAll(filteredCases().toList());
        for (Case scenario : cases) {
            Fixture f = factory.create(scenario, false, 2690);
            for (var entry : f.objects().entrySet()) {
                if (entry.getValue().main().isEmpty()) continue;
                // Golden outcomes independent of the expectedFiltered helper.
                boolean golden = scenario.family() == Family.FILTERED_ACTION
                        || (scenario.family() == Family.ALTERNATIVE_MATRIX
                        && (entry.getKey().equals(WorkedGroupSteps.LEFT_OBJECT_ID)
                        ? scenario.from() >= 2500 && scenario.from() < 5000 : scenario.from() < 2500));
                assertEquals(golden, expectedFiltered(f, entry.getValue()), scenario + "/" + entry.getKey());
                for (boolean report : List.of(false, true)) {
                    var base = response(f, report);
                    rejects(base, f, report, true, root -> {
                        var obj = (ObjectNode) WorkedGroupAssertions.find(root.path("splittingResults"), "objectId", entry.getKey());
                        ((ObjectNode) obj.path("objectFlags").get(0)).put("value", Boolean.toString(!golden));
                    });
                    for (String shape : List.of("missing", "null", "empty"))
                        rejects(base, f, report, true, root -> {
                            var obj = (ObjectNode) WorkedGroupAssertions.find(root.path("splittingResults"), "objectId", entry.getKey());
                            switch (shape) {
                                case "missing" -> obj.remove("objectFlags");
                                case "null" -> obj.putNull("objectFlags");
                                default -> obj.putArray("objectFlags");
                            }
                        });
                }
            }
        }
    }

    private static void mapperProfile() {
        try (var stream = SplitterDocumentContractChecks.class.getClassLoader().getResourceAsStream(
                "splitter/EXPLAB_2690/configmap/mapper-required.yml")) {
            assertNotNull(stream);
            var root = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper().readTree(stream);
            assertTrue(root.path("traffic-based-alternative").booleanValue());
            var rules = root.path("rules");
            var filter = rules.path("filter-rule");
            assertTrue(filter.path("enabled").booleanValue());
            assertEquals("mapperFilter", filter.path("proc-code").textValue());
            assertEquals("actionType", filter.path("proc-params").path("param-code").textValue());
            assertEquals("INTEGER", filter.path("proc-params").path("value-type").textValue());
            assertEquals(JSON.readTree("[2,4]"), filter.path("proc-params").path("values"));
            assertTrue(rules.path("filtered-flag-rule").path("enabled").booleanValue());
            assertEquals("filtered", rules.path("filtered-flag-rule").path("proc-params").path("flag-code").textValue());
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static void independentObjects(SplitterDocumentFixtures factory) {
        Fixture f = factory.create(new Case(Family.MULTI_OBJECT_GROUPS, 0, 2500), false, 2690L);
        var report = response(f, true);
        for (int i = 0; i < 2; i++) {
            var all = WorkedGroupAssertions.find(report.path("splittingResults").get(i).path("objectResults"), "ruleCode", "ALL");
            ((ObjectNode) all.path("resultExps").get(0)).put("salt", "wrong-object-" + i);
        }
        var error = assertThrows(MultipleFailuresError.class, () -> SplitterDocumentOracle.response(report, f, true, true));
        assertEquals(2, leaves(error).size(), "Both objects must be checked after the first failure");
        assertTrue(leaves(error).stream().allMatch(e -> e.getMessage().contains("salt")));
        mutations++;
    }
    private static List<Throwable> leaves(Throwable failure) {
        if (failure instanceof MultipleFailuresError many) return many.getFailures().stream().flatMap(e -> leaves(e).stream()).toList();
        return List.of(failure);
    }

    private static void goldenMatrix(SplitterDocumentFixtures factory) {
        int[] starts = {0, 500, 1000, 1500, 2500, 3000, 3500, 4000, 5000, 5500, 6000, 6500};
        int[] left = {2, 2, 1, 1, 1, 1, 1, 1, 2, 2, 0, 0};
        int[] right = {1, 1, 1, 1, 3, 1, 3, 1, 3, 0, 3, 0};
        for (int i = 0; i < starts.length; i++) {
            Fixture f = factory.create(new Case(Family.ALTERNATIVE_MATRIX, starts[i], starts[i] + 500), false, 2690);
            var objects = new ArrayList<>(f.objects().values());
            assertEquals(left[i], relativeMain(objects.get(0)), "Document p.17-31 left MAIN at " + starts[i]);
            assertEquals(right[i], relativeMain(objects.get(1)), "Document p.17-31 right MAIN at " + starts[i]);
        }
        Fixture same = factory.create(new Case(Family.SAME_CONDITION, 0, 2500), true, 2690);
        assertEquals(List.of(1, 1, 1), same.objects().get(WorkedGroupSteps.LEFT_OBJECT_ID).reportAll().stream().map(Row::condition).toList());
        for (Case test : layerCases().toList()) {
            Fixture f = factory.create(test, true, 2690);
            var expected = f.objects().get(WorkedGroupSteps.REACTIONS_OBJECT_ID);
            assertEquals(6, expected.reportAll().size());
            assertEquals(test.from() < 5000 ? 3 : test.from() == 5000 ? 1 : 0, expected.main().size());
        }
    }
    private static int relativeMain(ObjectResult object) { return object.main().isEmpty() ? 0 : (int) object.main().get(0).expId() - 269210; }

    private static void noMainWithAll(SplitterDocumentFixtures factory) {
        Fixture original = factory.create(new Case(Family.DIFFERENT_CONDITIONS, 0, 2500), true, 2690);
        var objects = new LinkedHashMap<>(original.objects());
        var linked = objects.get(WorkedGroupSteps.LEFT_OBJECT_ID);
        objects.put(WorkedGroupSteps.LEFT_OBJECT_ID, new ObjectResult(List.of(), linked.restAll(), linked.reportAll()));
        Fixture f = new Fixture(original.scenario(), true, original.config(), original.request(), original.spread(), objects);
        var api = response(f, false);
        SplitterDocumentOracle.response(api, f, false, true);
        assertThrows(AssertionError.class, () -> SplitterDocumentOracle.response(api, f, false, false));
        ((ObjectNode) api.path("splittingResults").get(0)).putArray("objectResults");
        SplitterDocumentOracle.response(api, f, false, false);
        assertThrows(AssertionError.class, () -> SplitterDocumentOracle.response(api, f, false, true));
        // Assertion branch only: the production REACTIONS profile always selects MAIN when a group worked.
        mutations += 2;
    }
}
