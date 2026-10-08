package util.splittercheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dto.splitter.config.WorkedGroupRequests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dto.splitter.config.WorkedGroupRequests.*;
import static util.splittercheck.WorkedGroupAssertions.*;

class CandidateSelectionChecks {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ObjectNode body() throws Exception {
        return (ObjectNode) JSON.readTree("""
            {"splittingResults":[{"objectId":"one","objectResults":[
              {"ruleCode":"ALL","resultExps":[{"expId":298401,"conditionId":1,"expGroup":"A","finalExpGroup":"B","expFlags":[]}]},
              {"ruleCode":"MAIN","resultExps":[]}]}]}
            """);
    }
    @Test void fullReportAllowsNonWorkedLinkedRowButNeverAsWinner() throws Exception {
        var root = body(); var object = root.path("splittingResults").get(0);
        rows(object, "ALL", List.of(new ExpectedRow(E1,1,"A","B")));
        rows(object, "MAIN", List.of());
        ((com.fasterxml.jackson.databind.node.ArrayNode)object.at("/objectResults/1/resultExps"))
                .add(object.at("/objectResults/0/resultExps/0").deepCopy());
        assertThrows(AssertionError.class, () -> rows(object,"MAIN",List.of()));
    }
    @ParameterizedTest @ValueSource(strings={"true","false","null"})
    void reactionsRejectsAlternativeRegardlessOfValue(String value) throws Exception {
        var root=body(); var flag=((ObjectNode)root.at("/splittingResults/0/objectResults/0/resultExps/0"))
                .putArray("expFlags").addObject().put("code","isAlternative");
        if (value.equals("null")) flag.putNull("value"); else flag.put("value",value);
        assertThrows(AssertionError.class, () -> candidateFlags(root,true));
    }
    @Test void mapperStillRejectsDuplicateCodes() throws Exception {
        var root=body(); var flags=((ObjectNode)root.at("/splittingResults/0/objectResults/0/resultExps/0")).putArray("expFlags");
        flags.addObject().put("code","isAlternative").put("value","true");
        assertDoesNotThrow(() -> candidateFlags(root,false));
        flags.add(flags.get(0).deepCopy());
        assertThrows(AssertionError.class, () -> candidateFlags(root,false));
    }
    @Test void oneNullFlagInMainIsNotAnEmptyArray() throws Exception {
        var root=body(); var row=root.at("/splittingResults/0/objectResults/0/resultExps/0").deepCopy();
        ((ObjectNode)row).putArray("expFlags").addObject().put("code","isAlternative").putNull("value");
        ((com.fasterxml.jackson.databind.node.ArrayNode)root.at("/splittingResults/0/objectResults/1/resultExps")).add(row);
        assertThrows(AssertionError.class, () -> candidateFlags(root,false));
    }
    @Test void duplicateRowsCannotDisappearThroughSetComparison() throws Exception {
        var root=body(); var object=root.path("splittingResults").get(0);
        var rows=(com.fasterxml.jackson.databind.node.ArrayNode)object.at("/objectResults/0/resultExps");
        rows.add(rows.get(0).deepCopy());
        assertThrows(AssertionError.class, () -> rows(object,"ALL",List.of(new ExpectedRow(E1,1,"A","B"))));
    }
    @Test void conditionAndWorkedGroupArePartOfIdentity() throws Exception {
        var root=body(); var object=root.path("splittingResults").get(0);
        assertThrows(AssertionError.class, () -> rows(object,"ALL",List.of(new ExpectedRow(E1,2,"A","B"))));
        assertThrows(AssertionError.class, () -> rows(object,"ALL",List.of(new ExpectedRow(E1,1,"A","A"))));
    }
    @Test void malformedAndDuplicateRulesAreNotEmptyResults() throws Exception {
        var object=(ObjectNode)body().path("splittingResults").get(0);
        ((ObjectNode)object.at("/objectResults/1")).putNull("resultExps");
        assertThrows(AssertionError.class, () -> rows(object,"MAIN",List.of()));
        object.putArray("objectResults").addObject().put("ruleCode","MAIN").putArray("resultExps");
        ((com.fasterxml.jackson.databind.node.ArrayNode)object.get("objectResults")).add(object.get("objectResults").get(0).deepCopy());
        assertThrows(AssertionError.class, () -> rows(object,"MAIN",List.of()));
    }
    @Test void reportCorrelationIsStrictNotRecursiveTextSearch() {
        var request=WorkedGroupRequests.request("s",List.of(LEFT));
        var envelope=JSON.createObjectNode().put("source","SPLITTER").put("requestId",request.getRequestId());
        envelope.putObject("messageInfo").put("splittingPoint","REACTIONS");
        var body=envelope.putObject("message").put("requestId",request.getRequestId()).put("splittingId","s").put("splittingConfigVersion",10);
        assertSame(body,reportBody(envelope,request,"REACTIONS",10));
        body.put("requestId","foreign");
        assertThrows(AssertionError.class, () -> reportBody(envelope,request,"REACTIONS",10));
    }
    @Test void fixturesKeepLiteralS1BindingsAndIndependentLayers() {
        var complex=WorkedGroupRequests.config(WorkedGroupRequests.Fixture.COMPLEX,"REACTIONS",10,3,false);
        var e1=complex.getSplittingConfig().getExperiments().get(0);
        assertEquals(1,e1.getGroups().get(0).getSplittingResults().get(0).getConditionId());
        assertEquals(List.of("2"), e1.getObjectSelectConditions().get(0).getRules().get(0).get(0).getValues());
        assertEquals(List.of("21"),e1.getGroups().get(0).getSplittingResults().get(0).getResultParams().get(0).getParamValues());
        var layered=WorkedGroupRequests.config(WorkedGroupRequests.Fixture.PRIORITY_REJECT,"REACTIONS",11,2,false)
                .getSplittingConfig().getExperiments();
        assertNotEquals(layered.get(0).getLayerId(),layered.get(1).getLayerId());
        assertEquals(1,layered.get(0).getLayerPriority()); assertEquals(3,layered.get(1).getLayerPriority());
    }
    @Test void fixtureReversalDoesNotMutateAnotherConfiguration() {
        var normal=WorkedGroupRequests.config(WorkedGroupRequests.Fixture.COMPLEX,"REACTIONS",10,3,false);
        var reversed=WorkedGroupRequests.config(WorkedGroupRequests.Fixture.COMPLEX,"REACTIONS",11,3,true);
        assertEquals(E1,normal.getSplittingConfig().getExperiments().get(0).getId());
        assertEquals(E2,reversed.getSplittingConfig().getExperiments().get(0).getId());
        assertEquals("A",normal.getSplittingConfig().getExperiments().get(1).getGroups().get(0).getCode());
        assertEquals("C",reversed.getSplittingConfig().getExperiments().get(0).getGroups().get(0).getCode());
    }

    @ParameterizedTest @ValueSource(strings={"winner", "params", "flags", "missingObject", "duplicateRow"})
    void completeOracleRejectsIndependentServiceRegressions(String mutation) throws Exception {
        var config=WorkedGroupRequests.config(WorkedGroupRequests.Fixture.SINGLE,"REACTIONS",10,2,false);
        var request=WorkedGroupRequests.request("s",List.of(LEFT,RIGHT));
        var root=JSON.createObjectNode(); var objects=root.putArray("splittingResults");
        var left=objects.addObject().put("objectId",LEFT); left.putArray("objectResults");
        var right=objects.addObject().put("objectId",RIGHT); var rules=right.putArray("objectResults");
        ObjectNode correct=(ObjectNode)JSON.readTree("""
            {"expId":298401,"conditionId":2,"expGroup":"B","finalExpGroup":"B","salt":"24096d2M1e",
             "layerId":null,"layerPriority":null,"spreadValue":2909,"expFlags":[],
             "groupResultParams":[{"paramCode":"result","paramValues":["2"],"dataType":"INTEGER"}]}
            """);
        rules.addObject().put("ruleCode","MAIN").putArray("resultExps").add(correct);
        rules.addObject().put("ruleCode","ALL").putArray("resultExps").add(correct.deepCopy());
        var expected=Map.of(LEFT,List.<ExpectedRow>of(),RIGHT,List.of(new ExpectedRow(E1,2,"B","B")));
        assertDoesNotThrow(() -> candidateResult(root,request,config,2909,expected,expected,true));
        switch(mutation) {
            case "winner" -> correct.put("expGroup","A");
            case "params" -> ((ObjectNode)correct.path("groupResultParams").get(0)).putArray("paramValues").add("wrong");
            case "flags" -> correct.putArray("expFlags").addObject().put("code","isAlternative").put("value","false");
            case "missingObject" -> objects.remove(0);
            case "duplicateRow" -> ((com.fasterxml.jackson.databind.node.ArrayNode)rules.get(0).get("resultExps")).add(correct.deepCopy());
        }
        assertThrows(AssertionError.class, () -> candidateResult(root,request,config,2909,expected,expected,true));
    }

    @ParameterizedTest @ValueSource(ints={1,2,3})
    void sharedLinkFixtureMatchesDocumentAndDoesNotSelectUnlinkedObject(int groupCount) {
        for (boolean reversed : List.of(false, true)) {
            var experiment = config(Fixture.SAME, "REACTIONS", 100, groupCount, reversed)
                    .getSplittingConfig().getExperiments().get(0);
            assertEquals(1, experiment.getObjectSelectConditions().size(),
                    "Document shared-link example has only condition 1; condition 2 creates an unintended KAP row");
            var condition = experiment.getObjectSelectConditions().get(0);
            assertEquals(1, condition.getId().intValue());
            assertEquals(List.of("1"), condition.getRules().get(0).get(0).getValues());
            assertTrue(experiment.getGroups().stream().flatMap(g -> g.getSplittingResults().stream())
                    .allMatch(result -> result.getConditionId().equals(condition.getId())));
        }
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void kapAllHonorsAllowWithoutMainAndPreservesRowsForWinner(boolean allow) throws Exception {
        var left = new ExpectedRow(E1, 1, "A", "B");
        var right = new ExpectedRow(E1, 2, "B", "B");
        var main = Map.of(LEFT, List.<ExpectedRow>of(), RIGHT, List.of(right));
        var all = Map.of(LEFT, List.of(left), RIGHT, List.of(right));
        var scenario = new steps.flow.splitter.workedgroup.CandidateSelectionSteps.Case(
                "T20-check", Fixture.SINGLE, 2500, 5000, 2, main, all);
        var expected = scenario.reportAll(allow);
        assertEquals(List.of(right), expected.get(RIGHT));
        assertEquals(allow ? List.of(left) : List.of(), expected.get(LEFT));
        var object = body().path("splittingResults").get(0);
        if (allow) assertDoesNotThrow(() -> rows(object, "ALL", expected.get(LEFT)));
        else {
            assertThrows(AssertionError.class, () -> rows(object, "ALL", expected.get(LEFT)), "Leaked ALL must still fail");
            ((ObjectNode)object).putArray("objectResults");
            assertDoesNotThrow(() -> rows(object, "ALL", expected.get(LEFT)));
        }
        assertEquals(List.of(left), scenario.reportAll().get(LEFT), "Do not mutate allow=true oracle");
    }
    @Test void deniedNoneHasExactEmptyKapAllEvenWhenAllowedNoneOracleIsOpen() {
        var main = Map.of(LEFT, List.<ExpectedRow>of(), RIGHT, List.<ExpectedRow>of());
        var scenario = new steps.flow.splitter.workedgroup.CandidateSelectionSteps.Case(
                "T20-NONE", Fixture.SINGLE, 5000, 7500, 2, main, null);
        assertNull(scenario.reportAll(true));
        assertEquals(main, scenario.reportAll(false));
    }
}
