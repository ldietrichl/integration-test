package util.splittercheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dto.splitter.config.WorkedGroupRequests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import steps.flow.splitter.workedgroup.MapperAlternativeMarkupSteps.Case;
import support.splitter.cases.MapperAlternativeMarkupCases;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static dto.splitter.config.WorkedGroupRequests.ALTERNATIVE_SALT;
import static dto.splitter.config.WorkedGroupRequests.alternativeConfig;
import static dto.splitter.config.WorkedGroupRequests.alternativeRequest;
import static dto.splitter.config.WorkedGroupRequests.alternativeObjectId;
import static support.splitter.cases.MapperAlternativeMarkupCases.*;
import static util.splittercheck.WorkedGroupAssertions.*;

class MapperAlternativeMarkupChecks {
    private static final ObjectMapper JSON = new ObjectMapper();
    static Stream<Case> fixtures() {
        return Stream.of(core(), permutations(), requestScope().stream(), resetFlags().stream(), changeLinks().stream(),
                alternateParameter(), reversedMarkup(), boundaries(), multipleLinkedGroups(), workingRules(),
                filterControls(), exactGroupPair(), fullFlagSequence().stream())
                .flatMap(s -> s);
    }
    @ParameterizedTest(name="{0}") @MethodSource("fixtures")
    void literalOracleUsesRealLinksAndWorkedGroups(Case scenario) {
        for (boolean reversed : List.of(false,true)) {
            var config=alternativeConfig(123,scenario.experiments(),reversed);
            Set<Integer> expIds=new HashSet<>();
            Map<String,List<ExpectedRow>> all=new LinkedHashMap<>();
            scenario.objects().forEach(o -> all.put(alternativeObjectId(o),new ArrayList<>()));
            for (var exp:config.getSplittingConfig().getExperiments()) {
                assertTrue(expIds.add(exp.getId()));
                assertEquals(ALTERNATIVE_SALT,exp.getSalt());
                var worked=exp.getGroups().stream().filter(g -> g.getShares().stream()
                        .anyMatch(s -> s.getShareFrom()<=scenario.spread() && s.getShareTo()>scenario.spread())).toList();
                assertTrue(worked.size()<=1,"Exactly one worked group or NONE; never overlapping shares");
                String finalGroup=worked.isEmpty()?null:worked.get(0).getCode();
                Set<Integer> referenced=new HashSet<>();
                for (var group:exp.getGroups()) for(var result:group.getSplittingResults()) {
                    int object=result.getConditionId(); referenced.add(object);
                    var condition=exp.getObjectSelectConditions().stream().filter(c -> c.getId()==object).findFirst().orElseThrow();
                    assertEquals(List.of(Integer.toString(object)),condition.getRules().get(0).get(0).getValues());
                    if (scenario.objects().contains(object)) all.get(alternativeObjectId(object)).add(
                            new ExpectedRow(exp.getId(),object,group.getCode(),finalGroup));
                    assertTrue(result.getResultParams().size()>=2);
                    assertDoesNotThrow(() -> Integer.parseInt(result.getResultParams().get(1).getParamValues().get(0)));
                }
                assertEquals(referenced,exp.getObjectSelectConditions().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet()),
                        "No orphan object conditions");
            }
            all.forEach((id,expected) -> assertEquals(new HashSet<>(expected),new HashSet<>(scenario.all().get(id)),"Literal ALL links: "+id));
            scenario.main().forEach((id,main) -> assertTrue(scenario.all().get(id).containsAll(main),"MAIN must be a linked row"));
        }
    }

    @Test void extendedProfilesPreserveMainAndChangeOnlyDeclaredRule() throws Exception {
        var yaml=new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        var source=java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/splitter/EXPLAB_3056/configmap/mapper-required.yml"));
        var original=yaml.readTree(source);
        for (var profile:steps.flow.splitter.workedgroup.MapperAlternativeMarkupSteps.Profile.values()) {
            var changed=yaml.readTree(profile.rules(source));
            assertEquals(original.path("rules").path("final-exp-rule"),changed.path("rules").path("final-exp-rule"));
            assertTrue(changed.path("traffic-based-alternative").booleanValue());
            assertEquals(original.at("/rules/filter-rule/proc-params"), changed.at("/rules/filter-rule/proc-params"),
                    "Profiles must not invent filter settings absent from the supported resource");
        }
        var P=steps.flow.splitter.workedgroup.MapperAlternativeMarkupSteps.Profile.ALT_PARAM;
        assertEquals("altAction",yaml.readTree(P.rules(source)).at("/rules/alternative-markup-rule/proc-params/param-code").textValue());
        var reversed=yaml.readTree(steps.flow.splitter.workedgroup.MapperAlternativeMarkupSteps.Profile.REVERSED_MARKUP.rules(source));
        assertEquals("[3,1]",reversed.at("/rules/alternative-markup-rule/proc-params/alt-markup-values").toString());
    }
    @Test void precalcUsesSplitKeysAndIndependentSnapshotOfParameters() {
        var request=alternativeRequest("precalc",List.of(1,2,99));
        var pre=dto.splitter.precalc.MapperPrecalcRequests.fromSplit(123,request);
        assertEquals(123,pre.path("soConfigVersion").intValue());
        assertEquals(3,pre.path("splittingObjects").size());
        assertEquals(request.getSplittingObjects().get(0).getUniqueConfigurationId(),pre.at("/splittingObjects/0/uniqueConfigurationId").textValue());
        request.getSplittingObjects().get(0).setObjectParams(List.of());
        assertEquals("1",pre.at("/splittingObjects/0/objectParams/0/paramValues/0").textValue());
    }
    @Test void independentPrecalcCasesCannotReuseCachedLinks() {
        var first = WorkedGroupRequests.alternativePrecalculatedRequest("same-spread", List.of(1, 2, 99));
        var second = WorkedGroupRequests.alternativePrecalculatedRequest("same-spread", List.of(1, 2, 99));
        Set<String> firstKeys = new HashSet<>();
        first.getSplittingObjects().forEach(o -> assertTrue(firstKeys.add(o.getUniqueConfigurationId())));
        assertEquals(first.getSplittingId(), second.getSplittingId());
        for (int i = 0; i < first.getSplittingObjects().size(); i++) {
            var left = first.getSplittingObjects().get(i);
            var right = second.getSplittingObjects().get(i);
            assertFalse(firstKeys.contains(right.getUniqueConfigurationId()), "Cached links must not cross case boundaries");
            assertEquals(left.getObjectId(), right.getObjectId());
            assertEquals(JSON.valueToTree(left.getObjectParams()), JSON.valueToTree(right.getObjectParams()));
        }
        var pre = dto.splitter.precalc.MapperPrecalcRequests.fromSplit(123, second);
        for (int i = 0; i < second.getSplittingObjects().size(); i++) {
            assertEquals(second.getSplittingObjects().get(i).getUniqueConfigurationId(),
                    pre.path("splittingObjects").get(i).path("uniqueConfigurationId").textValue());
        }
        second.getSplittingObjects().get(0).setObjectParams(List.of());
        assertEquals("1", pre.at("/splittingObjects/0/objectParams/0/paramValues/0").textValue());
    }
    @Test void fullSequenceKeepsConfigButExercisesAllThreeFlagStates() {
        var sequence=fullFlagSequence();
        assertEquals(1,sequence.stream().map(Case::experiments).distinct().count());
        assertEquals(Set.of(marked(1,row(E1,1,"A","B"))),sequence.get(0).marked());
        assertEquals(Set.of(marked(2,row(E1,2,"B","B"))),sequence.get(1).marked());
        assertTrue(sequence.get(2).marked().isEmpty());
        sequence.forEach(s -> assertDoesNotThrow(() -> check(s,reply(s))));
    }
    @Test void workingRollbackCandidateIsDifferentFromFinalExperiment() {
        Case scenario=workingRules().filter(c -> c.id().equals("3056-T11-W-IN-R-MAIN-OUT")).findFirst().orElseThrow();
        var main=scenario.main().get(alternativeObjectId(1)).get(0);
        assertEquals(row(E1,1,"A","B"), main);
        var config=alternativeConfig(123,scenario.experiments(),false);
        var finalExperiment=config.getSplittingConfig().getExperiments().stream()
                .filter(e -> e.getId()==main.expId()).findFirst().orElseThrow();
        // MAIN is outside R for either representation of its group; the test isolates experiment selection.
        for (var group:finalExperiment.getGroups()) for (var result:group.getSplittingResults()) {
            var action=result.getResultParams().stream().filter(p -> p.getParamCode().equals("actionType")).findFirst().orElseThrow();
            assertEquals(List.of("1"),action.getParamValues());
        }
        var candidate=config.getSplittingConfig().getExperiments().stream().filter(e -> e.getId()==E2).findFirst().orElseThrow();
        assertNotEquals(main.expId(),candidate.getId());
        assertEquals(List.of("5"),candidate.getGroups().stream().filter(g -> g.getCode().equals("D"))
                .findFirst().orElseThrow().getSplittingResults().get(0).getResultParams().stream()
                .filter(p -> p.getParamCode().equals("actionType")).findFirst().orElseThrow().getParamValues());
        assertEquals(Set.of(marked(1,row(E1,1,"A","B")),marked(1,row(E2,1,"C","D"))),scenario.marked());
        var body=reply(scenario);
        assertDoesNotThrow(() -> check(scenario,body));
        var sourceRows=ruleRows(find(body.path("splittingResults"),"objectId",alternativeObjectId(1)),"ALL");
        for (var result:sourceRows) if (result.path("expId").intValue()==E2)
            ((ObjectNode)result.path("expFlags").get(0)).put("value","false");
        assertThrows(AssertionError.class,() -> check(scenario,body),"Rollback driven by a non-MAIN candidate must be rejected");
    }

    @Test void unresolvedContractsAreExplicitAndNotPartOfActiveFixtures() {
        var gaps=unresolvedContracts().toList();
        assertEquals(11,gaps.size());
        assertEquals(gaps.size(),gaps.stream().map(ContractGap::id).distinct().count());
        gaps.forEach(g -> assertFalse(g.reason().isBlank()));
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,true,true,true", "0,true,false,false", "2,true,false,true",
            "4,true,false,true", "2,false,true,false", "0,false,true,false"})
    void filterControlsHaveIndependentEffectsAndRejectInvertedFlag(int action, boolean enabled,
                                                                   boolean filterAlternative, boolean expected) {
        var scenario=direct("filter",false,action,1,true);
        var body=reply(scenario);
        var source=(ObjectNode)find(body.path("splittingResults"),"objectId",alternativeObjectId(1));
        var receiver=(ObjectNode)find(body.path("splittingResults"),"objectId",alternativeObjectId(2));
        source.putArray("objectFlags").addObject().put("code","filtered").put("value",Boolean.toString(expected));
        receiver.putArray("objectFlags").addObject().put("code","filtered").put("value","false");
        var config=alternativeConfig(1,scenario.experiments(),false);
        assertDoesNotThrow(() -> alternativeFiltering(body,scenario.main(),scenario.marked(),config,enabled,filterAlternative));
        ((ObjectNode)source.path("objectFlags").get(0)).put("value",Boolean.toString(!expected));
        assertThrows(AssertionError.class, () -> alternativeFiltering(body,scenario.main(),scenario.marked(),config,enabled,filterAlternative));
    }

    @ParameterizedTest @ValueSource(ints={3,5,6})
    void finalWinnerRespectsAlternativeControlAfterPreselection(int action) {
        Case scenario=rollback("final-selection",action,false);
        var object=alternativeObjectId(1);
        var finalRow=action==3?row(E1,1,"A","B"):row(F,1,"F","F");
        assertEquals(List.of(finalRow),scenario.main().get(object),
                "mapperFinalExp v1.1.0 stage 6: 3 permits alternative selection; 5/6 preserve F");
        var body=reply(scenario);
        assertDoesNotThrow(() -> check(scenario,body));
        var wrong=ruleRows(find(body.path("splittingResults"),"objectId",object),"MAIN");
        ((ObjectNode)wrong.get(0)).put("expId",action==3?F:E1);
        assertThrows(AssertionError.class,() -> check(scenario,body));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void lowestAlternativeIdWinsIndependentlyOfMarkupEligibility(boolean secondCandidate) {
        Case scenario=multiple("final-min-id",false,secondCandidate);
        assertEquals(List.of(row(E1,1,"A","B")),scenario.main().get(alternativeObjectId(1)),
                "MAIN selection uses valueMap and minimum expId, independently of altMarkupValues");
        assertEquals(secondCandidate?2:1,scenario.marked().size());
        assertDoesNotThrow(() -> check(scenario,reply(scenario)));
    }

    private static ObjectNode reply(Case scenario) {
        var config=alternativeConfig(123,scenario.experiments(),false);
        ObjectNode body=JSON.createObjectNode(); var objects=body.putArray("splittingResults");
        scenario.all().forEach((id,all) -> {
            var object=objects.addObject().put("objectId",id); var rules=object.putArray("objectResults");
            for (String code:List.of("MAIN","ALL")) {
                var result=rules.addObject().put("ruleCode",code).putArray("resultExps");
                for (ExpectedRow row:code.equals("MAIN")?scenario.main().get(id):all) {
                    var source=config.getSplittingConfig().getExperiments().stream().filter(e -> e.getId()==row.expId()).findFirst().orElseThrow();
                    var params=source.getGroups().stream().filter(g -> g.getCode().equals(row.expGroup())).findFirst().orElseThrow()
                            .getSplittingResults().stream().filter(r -> r.getConditionId()==row.conditionId()).findFirst().orElseThrow().getResultParams();
                    var node=result.addObject().put("expId",row.expId()).put("conditionId",row.conditionId())
                            .put("expGroup",row.expGroup()).put("finalExpGroup",row.finalExpGroup()).put("salt",ALTERNATIVE_SALT)
                            .put("spreadValue",scenario.spread());
                    node.set("groupResultParams",JSON.valueToTree(params));
                    var flags=node.putArray("expFlags");
                    if(code.equals("ALL")) flags.addObject().put("code","isAlternative")
                            .put("value",Boolean.toString(scenario.marked().contains(new AlternativeRow(id,row))));
                }
            }
        });
        return body;
    }
    private static void check(Case scenario,ObjectNode body) {
        var config=alternativeConfig(123,scenario.experiments(),false);
        var request=alternativeRequest("local-fixture",scenario.objects());
        assertAll(() -> candidateResult(body,request,config,scenario.spread(),scenario.main(),scenario.all(),false),
                () -> alternativeFlags(body,scenario.all(),scenario.marked()));
    }
    @ParameterizedTest @ValueSource(strings={"clearTrue","markNormal","missingFlag","nullFlag","booleanFlag","duplicateFlag", "wrongGroup", "wrongCondition", "wrongMain", "wrongParams", "duplicateRow", "missingObject"})
    void oracleRejectsServiceRegressions(String mutation) {
        Case scenario=direct("local",false,0,1,true);
        var body=reply(scenario);
        assertDoesNotThrow(() -> check(scenario,body));
        var object=(ObjectNode)find(body.path("splittingResults"),"objectId",alternativeObjectId(1));
        var row=(ObjectNode)ruleRows(object,"ALL").get(0);
        var flag=(ObjectNode)row.path("expFlags").get(0);
        switch(mutation) {
            case "clearTrue" -> flag.put("value","false");
            case "markNormal" -> ((ObjectNode)ruleRows(find(body.path("splittingResults"),"objectId",alternativeObjectId(2)),"ALL")
                    .get(0).path("expFlags").get(0)).put("value","true");
            case "missingFlag" -> row.putArray("expFlags");
            case "nullFlag" -> flag.putNull("value");
            case "booleanFlag" -> flag.put("value",true);
            case "duplicateFlag" -> ((ArrayNode)row.path("expFlags")).add(flag.deepCopy());
            case "wrongGroup" -> row.put("expGroup","B");
            case "wrongCondition" -> row.put("conditionId",2);
            case "wrongMain" -> ((ObjectNode)ruleRows(object,"MAIN").get(0)).put("expId",E2);
            case "wrongParams" -> ((ObjectNode)row.path("groupResultParams").get(0)).putArray("paramValues").add("999");
            case "duplicateRow" -> ((ArrayNode)ruleRows(object,"ALL")).add(row.deepCopy());
            case "missingObject" -> ((ArrayNode)body.path("splittingResults")).remove(0);
        }
        assertThrows(AssertionError.class,() -> check(scenario,body));
    }
    @Test void rollbackMarksWorkedRowWhileDirectMarkupMarksUnworkedRow() {
        Case direct=direct("direct",false,0,1,true), rollback=rollback("rollback",5,false);
        assertEquals(Set.of(marked(1,row(E1,1,"A","B"))),direct.marked());
        assertEquals(Set.of(marked(2,row(E1,2,"B","B"))),rollback.marked());
        assertDoesNotThrow(() -> check(rollback,reply(rollback)));
        var wrong=reply(rollback);
        var receiver=find(wrong.path("splittingResults"),"objectId",alternativeObjectId(2));
        ((ObjectNode)ruleRows(receiver,"ALL").get(0).path("expFlags").get(0)).put("value","false");
        assertThrows(AssertionError.class,() -> check(rollback,wrong));
        assertTrue(direct.publicMarked().isEmpty());
        assertEquals(rollback.marked(),rollback.publicMarked());
    }
    @Test void changingRequestScopeChangesExpectedRollbackWithoutChangingConfig() {
        var sequence=requestScope();
        assertEquals(sequence.get(0).experiments(),sequence.get(1).experiments());
        assertFalse(sequence.get(0).marked().isEmpty()); assertTrue(sequence.get(1).marked().isEmpty());
        assertEquals(sequence.get(0),sequence.get(2));
    }
    @Test void exactPairDoesNotMarkAnotherExperimentWithSameGroupCode() {
        Case scenario=multiple("multiple",true,true);
        assertEquals(Set.of(marked(2,row(E1,2,"B","B")),marked(3,row(E2,3,"D","D"))),scenario.marked());
        var body=reply(scenario);
        assertDoesNotThrow(() -> check(scenario,body));
        var unrelated=find(body.path("splittingResults"),"objectId",alternativeObjectId(99));
        ((ObjectNode)ruleRows(unrelated,"ALL").get(0).path("expFlags").get(0)).put("value","true");
        assertThrows(AssertionError.class,() -> check(scenario,body));
    }
}
