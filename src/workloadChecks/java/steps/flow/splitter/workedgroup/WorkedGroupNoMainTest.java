package steps.flow.splitter.workedgroup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.splittercheck.WorkedGroupAssertions;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WorkedGroupNoMainTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static ObjectNode response() throws Exception {
        return (ObjectNode) JSON.readTree("""
            {"splittingResults":[{"objectId":"one","objectFlags":[{"code":"filtered","value":"false"}],
            "objectResults":[{"ruleCode":"ALL","resultExps":[{"expId":269012,"conditionId":1,
            "expGroup":"A","finalExpGroup":"A","salt":"EXPLAB-2690-SALT-NO-MAIN",
            "layerId":null,"layerPriority":null,"spreadValue":10,
            "expFlags":[{"code":"isAlternative","value":"false"}],
            "groupResultParams":[{"paramCode":"result","paramValues":["999"],"dataType":"INTEGER"}]}]},
            {"ruleCode":"MAIN","resultExps":[]}]}]}
            """);
    }
    private static ObjectNode exp(ObjectNode root) { return (ObjectNode) root.at("/splittingResults/0/objectResults/0/resultExps/0"); }
    private static final class Probe extends MapperMixedObjectsSteps {
        private final ObjectNode root;
        private final boolean allow;
        Probe(ObjectNode root, boolean allow) { this.root=root; this.allow=allow; }
        @Override protected boolean allowResultWithoutMain() { return allow; }
        @Override protected JsonNode jsonBody(ValidatableResponseWrapper unused, String context) { return root; }
        @Override protected String body(ValidatableResponseWrapper unused) { return root.toString(); }
        @Override protected void assertConfiguredExperiment(ValidatableResponseWrapper unused, JsonNode row) {
            WorkedGroupAssertions.experiment(row, noMainExperiment(), 10);
        }
        void verify() { assertObjectWithoutMain(null, "one", Map.of(269012L, "A")); }
    }
    @Test void finalExperimentsPreparationAndMetadataTargetReactions() {
        var steps = new ReactionsFinalExperimentsSteps() { };
        assertEquals("REACTIONS", steps.runtimeSplittingPoint());
    }
    @Test void allowsExactWorkedAllWithoutMain() throws Exception { new Probe(response(), true).verify(); }
    @Test void deniesAllWhenProfileDisallowsNoMain() throws Exception {
        var root=response(); assertThrows(AssertionError.class, () -> new Probe(root,false).verify());
        ((ObjectNode)root.at("/splittingResults/0")).putArray("objectResults"); new Probe(root,false).verify();
    }
    @Test void requiresTechnicalEmptyMainWithValidAll() throws Exception {
        var root=response(); ((ObjectNode)root.at("/splittingResults/0")).withArray("objectResults").remove(1);
        assertThrows(AssertionError.class, () -> new Probe(root,true).verify());
    }
    @Test void rejectsNonEmptyNullAndMissingMainRows() throws Exception {
        for (String shape : new String[]{"nonempty", "null", "missing", "object"}) {
            var root=response(); var main=(ObjectNode)root.at("/splittingResults/0/objectResults/1");
            switch (shape) {
                case "nonempty" -> main.withArray("resultExps").add(exp(root).deepCopy());
                case "null" -> main.putNull("resultExps");
                case "missing" -> main.remove("resultExps");
                default -> main.putObject("resultExps");
            }
            assertThrows(AssertionError.class, () -> new Probe(root,true).verify(),shape);
        }
    }
    @Test void rejectsWrongGroupsIdentityConditionAndValues() throws Exception {
        for (String field : new String[]{"expId","conditionId","expGroup","finalExpGroup","salt","spreadValue"}) {
            var root=response(); if (exp(root).get(field).isNumber()) exp(root).put(field,99999); else exp(root).put(field,"wrong");
            assertThrows(AssertionError.class, () -> new Probe(root,true).verify(),field);
        }
        var root=response(); ((ObjectNode)exp(root).path("groupResultParams").get(0)).putArray("paramValues").add("wrong");
        assertThrows(AssertionError.class, () -> new Probe(root,true).verify());
    }
    @Test void rejectsExtraAndDuplicateRows() throws Exception {
        for (long id : new long[]{269012,99999}) {
            var root=response(); var extra=exp(root).deepCopy().put("expId",id);
            ((ObjectNode)root.at("/splittingResults/0/objectResults/0")).withArray("resultExps").add(extra);
            assertThrows(AssertionError.class, () -> new Probe(root,true).verify());
        }
    }
    @Test void requiresWorkedAllAndRejectsAlternativeFlag() throws Exception {
        var root=response(); ((ObjectNode)root.at("/splittingResults/0")).putArray("objectResults");
        assertThrows(AssertionError.class, () -> new Probe(root,true).verify());
        var flags=response(); ((ObjectNode)exp(flags).path("expFlags").get(0)).put("value","true");
        assertThrows(AssertionError.class, () -> new Probe(flags,true).verify());
    }
}
