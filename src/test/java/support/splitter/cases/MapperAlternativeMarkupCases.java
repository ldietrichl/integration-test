package support.splitter.cases;

import dto.splitter.config.WorkedGroupRequests.AlternativeExperiment;
import dto.splitter.config.WorkedGroupRequests.AlternativeGroup;
import steps.flow.splitter.workedgroup.MapperAlternativeMarkupSteps.Case;
import util.splittercheck.WorkedGroupAssertions.AlternativeRow;
import util.splittercheck.WorkedGroupAssertions.ExpectedRow;
import java.util.*;
import java.util.stream.Stream;
import static dto.splitter.config.WorkedGroupRequests.alternativeObjectId;

/** Literal expected winners and flags; no copy of mapperAlternative is used as the oracle. */
public final class MapperAlternativeMarkupCases {
    private MapperAlternativeMarkupCases() { }
    public static final int E1 = 305601, F = 305602, E2 = 305603, E3 = 305604;
    public static AlternativeGroup group(String code, int from, int to, int action, Integer... objects) {
        return new AlternativeGroup(code, from, to, action, List.of(objects));
    }
    public static AlternativeExperiment exp(int id, AlternativeGroup... groups) {
        return new AlternativeExperiment(id, List.of(groups));
    }
    public static ExpectedRow row(int id, int object, String group, String worked) {
        return new ExpectedRow(id, object, group, worked);
    }
    public static AlternativeRow marked(int object, ExpectedRow row) { return new AlternativeRow(alternativeObjectId(object), row); }
    private static Map<String, List<ExpectedRow>> rows(Map<Integer, List<ExpectedRow>> values) {
        Map<String, List<ExpectedRow>> result = new LinkedHashMap<>();
        values.forEach((id, rows) -> result.put(alternativeObjectId(id), rows)); return result;
    }
    private static Case scenario(String id, List<AlternativeExperiment> exps, int spread,
                                 Map<Integer, List<ExpectedRow>> main, Map<Integer, List<ExpectedRow>> all,
                                 AlternativeRow... marked) {
        return new Case(id, exps, spread, main.keySet().stream().sorted().toList(), rows(main), rows(all), Set.of(marked));
    }

    public static Case direct(String id, boolean aWorked, int actionA, int actionB, boolean alternative) {
        String worked = aWorked ? "A" : "B";
        var a = row(E1, 1, "A", worked); var b = row(E1, 2, "B", worked);
        var expected = Map.of(1, List.of(a), 2, List.of(b), 99, List.<ExpectedRow>of());
        return scenario(id, List.of(exp(E1, group("A",0,5000,actionA,1), group("B",5000,10000,actionB,2))),
                aWorked ? 1000 : 6000, expected, expected,
                alternative ? new AlternativeRow[]{aWorked ? marked(2,b) : marked(1,a)} : new AlternativeRow[]{});
    }
    public static Case shared(String id, int actionB) {
        var a = row(E1,1,"A","B"); var b = row(E1,1,"B","B");
        return scenario(id, List.of(exp(E1,group("A",0,5000,0,1),group("B",5000,10000,actionB,1))),6000,
                Map.of(1,List.of(b),2,List.of(),99,List.of()), Map.of(1,List.of(a,b),2,List.of(),99,List.of()));
    }
    public static Case unrelatedExperiment() {
        Case base=direct("3056-T04",false,0,1,true);
        List<AlternativeExperiment> definitions=new ArrayList<>(base.experiments());
        definitions.add(exp(E3,group("B",0,10000,1,7)));
        return new Case(base.id(),definitions,base.spread(),base.objects(),base.main(),base.all(),base.marked());
    }
    public static Case sharedThree(String worked, int spread) {
        var a=row(E1,1,"A",worked); var b=row(E1,1,"B",worked); var c=row(E1,1,"C",worked);
        return scenario("3056-T03-THREE-"+worked, List.of(exp(E1,group("A",0,2500,0,1),
                group("B",2500,5000,1,1),group("C",5000,10000,3,1))),spread,
                Map.of(1,List.of(worked.equals("B")?b:c),99,List.of()),Map.of(1,List.of(a,b,c),99,List.of()));
    }
    public static Case rollback(String id, int sourceMain, boolean protectedReceiver) {
        var a=row(E1,1,"A","B"); var b=row(E1,2,"B","B"); var f=row(F,1,"F","F");
        List<AlternativeExperiment> exps=new ArrayList<>(List.of(exp(E1,group("A",0,5000,0,1),group("B",5000,10000,1,2)),
                exp(F,group("F",0,10000,sourceMain,1))));
        var receiverMain=b; List<ExpectedRow> receiverAll=List.of(b);
        if (protectedReceiver) {
            exps.add(exp(E2,group("F",0,10000,5,2)));
            receiverMain=row(E2,2,"F","F"); receiverAll=List.of(b,receiverMain);
        }
        boolean rollback=sourceMain==5 || sourceMain==6;
        AlternativeRow[] flags=rollback ? protectedReceiver ? new AlternativeRow[]{} : new AlternativeRow[]{marked(2,b)}
                : new AlternativeRow[]{marked(1,a)};
        // mapperFinalExp stage 6 replaces the preliminary F unless its value is in altControl=[5,6].
        return scenario(id,exps,6000,Map.of(1,List.of(rollback?f:a),2,List.of(receiverMain),99,List.of()),
                Map.of(1,List.of(a,f),2,receiverAll,99,List.of()),flags);
    }
    public static Case multiple(String id, boolean rollback, boolean secondCandidate) {
        var a=row(E1,1,"A","B"); var b=row(E1,2,"B","B");
        var c=row(E2,1,"C","D"); var d=row(E2,3,"D","D"); var f=row(F,1,"F","F");
        // E3.B shares a code with the rollback pair E1.B, but must never receive its flag.
        var unrelated=row(E3,99,"B","B");
        List<AlternativeRow> marked=new ArrayList<>();
        marked.add(rollback?marked(2,b):marked(1,a));
        if (secondCandidate) marked.add(rollback?marked(3,d):marked(1,c));
        return scenario(id,List.of(exp(E1,group("A",0,5000,0,1),group("B",5000,10000,1,2)),
                        exp(E2,group("C",0,5000,0,1),group("D",5000,10000,secondCandidate?3:0,3)),
                        exp(F,group("F",0,10000,rollback?5:3,1)),exp(E3,group("B",0,10000,1,99))),6000,
                // Outside altControl the final selection uses the lowest alternative expId: E1.
                Map.of(1,List.of(rollback?f:a),2,List.of(b),3,List.of(d),99,List.of(unrelated)),
                Map.of(1,List.of(a,c,f),2,List.of(b),3,List.of(d),99,List.of(unrelated)),marked.toArray(AlternativeRow[]::new));
    }
    public static Case duplicateRollback() {
        var a=row(E1,1,"A","B"); var a4=row(E1,4,"A","B"); var b=row(E1,2,"B","B");
        var f=row(F,1,"F","F"); var f4=row(F,4,"F","F");
        return scenario("3056-T16",List.of(exp(E1,group("A",0,5000,0,1,4),group("B",5000,10000,1,2)),
                        exp(F,group("F",0,10000,5,1,4))),6000,
                Map.of(1,List.of(f),4,List.of(f4),2,List.of(b)),
                Map.of(1,List.of(a,f),4,List.of(a4,f4),2,List.of(b)),marked(2,b));
    }
    public static Case receivers() {
        var a=row(E1,1,"A","B"); var b=row(E1,2,"B","B");
        var b3=row(E1,3,"B","B"); var b4=row(E1,4,"B","B");
        var f=row(F,1,"F","F"); var f4=row(F,4,"F","F");
        return scenario("3056-T17",List.of(exp(E1,group("A",0,5000,0,1),group("B",5000,10000,1,2,3,4)),
                        exp(F,group("F",0,10000,5,1,4))),6000,
                Map.of(1,List.of(f),2,List.of(b),3,List.of(b3),4,List.of(f4)),
                Map.of(1,List.of(a,f),2,List.of(b),3,List.of(b3),4,List.of(b4,f4)),marked(2,b),marked(3,b3));
    }
    public static Case combinedBranches() {
        var a=row(E1,1,"A","B"); var a3=row(E1,3,"A","B"); var b=row(E1,2,"B","B");
        var f=row(F,3,"F","F");
        return scenario("3056-T24",List.of(exp(E1,group("A",0,5000,0,1,3),group("B",5000,10000,1,2)),
                        exp(F,group("F",0,10000,5,3))),6000,
                Map.of(1,List.of(a),2,List.of(b),3,List.of(f)),
                Map.of(1,List.of(a),2,List.of(b),3,List.of(a3,f)),marked(1,a),marked(2,b));
    }
    public static Stream<Case> core() {
        return Stream.of(direct("3056-T01-T06",false,0,1,true),direct("3056-T02",true,1,0,true),
                shared("3056-T03-T05-LINKED-IN",1),shared("3056-T05-LINKED-OUT",0),
                sharedThree("B",3000),sharedThree("C",6000),unrelatedExperiment(),
                direct("3056-T05-T06-NOT-LINKED-OUT",false,1,0,false),direct("3056-T08-SECOND-MARKUP",false,0,3,true),
                rollback("3056-T10-T11",5,false),rollback("3056-T12-SECOND-ROLLBACK",6,false),
                rollback("3056-T12-NO-ROLLBACK",3,false),rollback("3056-T13",5,true),
                multiple("3056-T09-ALL",false,true),multiple("3056-T09-ONE",false,false),
                multiple("3056-T14-T15",true,true),duplicateRollback(),receivers(),combinedBranches());
    }
    public static Stream<Case> permutations() {
        return Stream.of(rollback("3056-T18-ROLLBACK",5,false),multiple("3056-T18-MULTIPLE",true,true),receivers());
    }
    public static List<Case> requestScope() {
        Case joint=rollback("3056-T19-JOINT",5,false);
        String id=alternativeObjectId(2);
        Case single=new Case("3056-T19-RECEIVER-ONLY",joint.experiments(),joint.spread(),List.of(2),
                Map.of(id,joint.main().get(id)),Map.of(id,joint.all().get(id)),Set.of());
        return List.of(joint,single,joint);
    }
    public static List<Case> resetFlags() {
        Case rollback=rollback("3056-T20-ROLLBACK",5,false);
        var a=row(E1,1,"A","A"); var b=row(E1,2,"B","A"); var f=row(F,1,"F","F");
        Case none=scenario("3056-T20-NO-CANDIDATE",rollback.experiments(),1000,
                Map.of(1,List.of(f),2,List.of(b),99,List.of()),Map.of(1,List.of(a,f),2,List.of(b),99,List.of()));
        return List.of(rollback,none,rollback,none,rollback);
    }
    public static List<Case> changeLinks() {
        Case original=direct("3056-T21-BEFORE",false,0,1,true);
        Case moved=shared("3056-T21-MOVED",1);
        return List.of(original,moved,original);
    }

    public static Stream<Case> alternateParameter() {
        return Stream.of(false,true).map(mark -> {
            var a=row(E1,1,"A","B"); var b=row(E1,2,"B","B");
            var ga=new AlternativeGroup("A",0,5000,0,List.of(1),List.of(
                    new dto.splitter.common.ParamDto("altAction",List.of("0"),"INTEGER")));
            var gb=new AlternativeGroup("B",5000,10000,mark?0:1,List.of(2),List.of(
                    new dto.splitter.common.ParamDto("altAction",List.of(mark?"1":"0"),"INTEGER")));
            var expected=Map.of(1,List.of(a),2,List.of(b),99,List.<ExpectedRow>of());
            return scenario("3056-T07-"+(mark?"ALT-IN-ACTION-OUT":"ALT-OUT-ACTION-IN"),List.of(exp(E1,ga,gb)),6000,
                    expected,expected,mark?new AlternativeRow[]{marked(1,a)}:new AlternativeRow[]{});
        });
    }
    public static Stream<Case> reversedMarkup() {
        return Stream.of(direct("3056-T08-REVERSED-FIRST",false,0,1,true),
                direct("3056-T08-REVERSED-SECOND",false,0,3,true),
                direct("3056-T08-REVERSED-OUT",false,1,0,false));
    }
    public static Stream<Case> boundaries() {
        return Stream.of(0,2499,2500,4999,5000,9999).map(spread -> {
            String worked=spread<2500?"A":spread<5000?"B":null;
            var a=row(E1,1,"A",worked); var b=row(E1,2,"B",worked);
            var f1=row(F,1,"F","F"); var f2=row(F,2,"F","F");
            return scenario("3056-T22-"+spread,List.of(exp(E1,group("A",0,2500,0,1),group("B",2500,5000,1,2)),
                            exp(F,group("F",0,10000,5,1,2))),spread,
                    Map.of(1,List.of(f1),2,List.of(f2),99,List.of()),Map.of(1,List.of(a,f1),2,List.of(b,f2),99,List.of()),
                    "B".equals(worked)?new AlternativeRow[]{marked(1,a)}:new AlternativeRow[]{});
        });
    }
    public static Stream<Case> multipleLinkedGroups() {
        var a=row(E1,1,"A","B"); var c=row(E1,1,"C","B"); var b=row(E1,2,"B","B"); var f=row(F,1,"F","F");
        return Stream.of(scenario("3056-T23-MULTIPLE-LINKS",List.of(exp(E1,group("A",0,2500,0,1),
                        group("C",2500,5000,0,1),group("B",5000,10000,1,2)),exp(F,group("F",0,10000,5,1))),6000,
                Map.of(1,List.of(f),2,List.of(b)),Map.of(1,List.of(a,c,f),2,List.of(b)),marked(1,a),marked(1,c)));
    }
    public static Stream<Case> workingRules() {
        var a=row(E1,1,"A","B"); var b=row(E1,2,"B","B"); var f=row(F,1,"F","F"); var g=row(E2,2,"G","G");
        Case overlap=scenario("3056-T25-INTERSECTION",List.of(exp(E1,group("A",0,5000,0,1),group("B",5000,10000,5,2)),
                        exp(F,group("F",0,10000,5,1)),exp(E2,group("G",0,10000,3,2))),6000,
                Map.of(1,List.of(f),2,List.of(g)),Map.of(1,List.of(a,f),2,List.of(b,g)),marked(2,b));
        var c=row(E2,1,"C","D"); var d=row(E2,3,"D","D");
        // MAIN is E1 with both linked and worked actionType=1; E2.D=5 is a different candidate.
        // E1 wins mapperFinalExp stage 6 by minimum expId, so E2 must not cause rollback.
        Case workingCandidateInRollback=scenario("3056-T11-W-IN-R-MAIN-OUT",List.of(
                exp(E1,group("A",0,5000,1,1),group("B",5000,10000,1,2)),
                exp(E2,group("C",0,5000,0,1),group("D",5000,10000,5,3))),6000,
                Map.of(1,List.of(a),2,List.of(b),3,List.of(d),99,List.of()),
                Map.of(1,List.of(a,c),2,List.of(b),3,List.of(d),99,List.of()),marked(1,a),marked(1,c));
        return Stream.of(workingCandidateInRollback,overlap,
                direct("3056-T28-WORKING-DIRECT",false,0,1,true),rollback("3056-T28-WORKING-ROLLBACK",5,false),
                direct("3056-T28-FILTER-ACTION-2",false,0,2,true),direct("3056-T28-FILTER-ACTION-4",false,0,4,true));
    }
    public static Stream<Case> filterControls() {
        return Stream.of(direct("3056-T28-FILTER-DIRECT",false,0,1,true),rollback("3056-T28-FILTER-ROLLBACK",5,false),
                direct("3056-T28-FILTER-PARAM",false,0,2,false));
    }
    public static Stream<Case> exactGroupPair() {
        var a=row(E1,1,"A","B"); var b=row(E1,2,"B","B"); var c=row(E1,3,"C","B");
        var f=row(F,1,"F","F"); var f3=row(F,3,"F","F");
        return Stream.of(scenario("3056-T15-SAME-EXP-DIFFERENT-GROUP",List.of(exp(E1,
                        group("A",0,2500,0,1),group("C",2500,5000,0,3),group("B",5000,10000,1,2)),
                        exp(F,group("F",0,10000,5,1,3))),6000,
                Map.of(1,List.of(f),2,List.of(b),3,List.of(f3)),Map.of(1,List.of(a,f),2,List.of(b),3,List.of(c,f3)),marked(2,b)));
    }
    public static List<Case> fullFlagSequence() {
        var definitions=List.of(exp(E1,group("A",0,5000,0,1),group("B",5000,10000,1,2)),
                exp(F,group("F",0,6000,5,1),group("G",6000,7000,3,1),group("H",7000,10000,5,1)));
        List<Case> sequence=new ArrayList<>();
        for (int spread:List.of(6500,8000,1000,6500,8000)) {
            String worked=spread<5000?"A":"B", fw=spread<6000?"F":spread<7000?"G":"H";
            var a=row(E1,1,"A",worked);var b=row(E1,2,"B",worked);
            var f=row(F,1,"F",fw);var g=row(F,1,"G",fw);var h=row(F,1,"H",fw);
            var winner=spread==6500?a:spread==8000?h:f;
            sequence.add(scenario("3056-T20-FULL-"+spread,definitions,spread,
                    Map.of(1,List.of(winner),2,List.of(b)),Map.of(1,List.of(a,f,g,h),2,List.of(b)),
                    spread==6500?new AlternativeRow[]{marked(1,a)}:spread==8000?new AlternativeRow[]{marked(2,b)}:new AlternativeRow[]{}));
        }
        return sequence;
    }
    public static Stream<Case> reactionsControl() {
        Case base=direct("3056-T30-SEPARATE",false,0,1,false);
        var b=row(E1,2,"B","B");
        Case separate=scenario(base.id(),base.experiments(),base.spread(),Map.of(1,List.of(),2,List.of(b),99,List.of()),
                Map.of(1,List.of(),2,List.of(b),99,List.of()));
        return Stream.of(separate,shared("3056-T30-SHARED",1));
    }

    /** Visible contract gaps; these are never counted as implemented business assertions. */
    public record ContractGap(String id, String reason) { @Override public String toString() { return id+": "+reason; } }
    public static Stream<ContractGap> unresolvedContracts() {
        return Stream.of(
                new ContractGap("3056-T25-EMPTY-M", "Q3: no acceptance/rejection contract for empty altMarkupValues"),
                new ContractGap("3056-T25-EMPTY-R", "Q3: no acceptance/rejection contract for empty altRollbackValues"),
                new ContractGap("3056-T26-NO-MAIN-SOURCE", "Q2: rollback decision without MAIN is unspecified"),
                new ContractGap("3056-T26-NO-MAIN-RECEIVER", "Q2: rollback recipient without MAIN is unspecified"),
                new ContractGap("3056-T26-MISSING-PARAM", "Q3: missing parameter error/result is unspecified"),
                new ContractGap("3056-T26-NULL-PARAM", "Q3: null parameter error/result is unspecified"),
                new ContractGap("3056-T26-EMPTY-PARAM", "Q3: empty parameter values error/result is unspecified"),
                new ContractGap("3056-T26-MULTI-PARAM", "Q3: multi-valued parameter selection is unspecified"),
                new ContractGap("3056-T26-WRONG-TYPE", "Q3: parameter type mismatch error is unspecified"),
                new ContractGap("3056-T26-UNKNOWN-PROC", "Q3: unknown procedure startup error is unspecified"),
                new ContractGap("3056-T28-TRAFFIC-OFF", "Q4: contradictory traffic-based-alternative description p.424/6432"));
    }
}
